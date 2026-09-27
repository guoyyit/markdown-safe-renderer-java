# markdown-safe-renderer-java

面向并发请求的文档渲染与净化服务。接收未可信的 Markdown/HTML 文档，返回
**可遍历的结构化 AST** 与 **净化后的受限 HTML**，在输入不完整或恶意时保持
确定且可恢复的行为。

## 架构

```
RenderController (REST)  →  RenderService (编排)
                               ├─ OptionsValidator / OptionsAuditLog   选项校验与审计
                               ├─ RenderCache (LRU + TTL)              并发安全缓存
                               ├─ MarkdownParser                       块级+行内解析 → AST
                               ├─ HeadingNumbering / AnchorRegistry    标题编号、锚点、目录
                               └─ HtmlRenderer ── HtmlSanitizer        AST → 受限 HTML
```

`ResourceLimits` / `RuntimeBudget` 贯穿解析全程，超限即抛出
`LimitExceededException` 并映射为可区分的拒绝原因。

## 安全模型

**原则：默认拒绝，白名单放行，输出一律转义。**

- **标签白名单**：仅 `SanitizerPolicy` 声明的标签可出现在输出中；未列出的
  标签被移除（其文本内容保留）。
- **危险内容标签**：`script`、`style`、`iframe`、`object`、`embed`、`form`、
  `textarea`、`template`、`svg`、`math` 等标签连同其**全部内容**一并丢弃，
  不会以文本形式泄漏到输出。
- **属性白名单**：仅声明的标签-属性组合保留；所有 `on*` 事件处理属性无条件
  移除（兜底，不依赖白名单配置正确）。
- **URL 协议白名单**：`href`/`src` 等 URL 属性先做 **HTML 实体解码**、去除
  空白与控制字符，再小写比较协议；仅 `http`、`https`、`mailto` 与相对 URL
  放行。`javascript:`、`data:` 等协议的大小写变形、实体编码变形
  （`&#106;avascript:`）、控制字符变形（`java\tscript:`）均被拒绝。
- **属性逃逸防护**：属性值输出前转义 `"` `'` `&` `<` `>`，无法借属性值
  突破引号边界注入新标签。
- **文本一律转义**：Markdown 文本节点输出前转义 `&` `<` `>`；代码块内容
  从不被解释执行。
- **Markdown 链接/图片同样受协议检查**：`[x](javascript:...)` 退化为纯文本。
- **嵌套与大小写不构成绕过**：标签名统一小写后匹配；任意深度的嵌套危险
  标签同样被整体丢弃。

## 局部错误恢复

未闭合代码围栏、列数不匹配的表格行、未闭合链接等**不会**导致整篇解析
失败。每个问题产生一条 `ParseError`（严重级别、行/列位置、跳过原因），
问题片段被跳过或降级为纯文本，其余内容正常解析。修复输入后重试即可
得到无错误的解析结果。

## 标题编号与目录

- 编号按层级独立计数：`# A → ### C`（跳级）得到 `1` 与 `1.0.1`，结果确定。
- 锚点由标题文本 slug 化生成；重复标题自动追加 `-2`、`-3` 后缀，保证
  **全局唯一且稳定**（相同输入永远得到相同锚点）。
- 跨文档引用冲突：请求可携带 `reservedAnchors`，这些锚点不会被分配给
  本文档标题。
- 目录链接（`#anchor`）与渲染输出的标题 `id` 严格一致（有测试保证）。

## 缓存语义

- 缓存键 = SHA-256(选项版本 + 生效选项 + 保留锚点 + 原文)。**输入任何
  变化都会得到不同的键**，旧条目自然失效，永远不会返回过期结果。
- 计算在锁外进行，**只有完整构造成功的结果才会写入**；计算失败（超时、
  资源超限、内部异常）不写入缓存——超限与失败永远不会被缓存放大。
- 条目有 TTL（`renderer.cache.ttl-millis`），过期即视为未命中并重新计算。
- 容量有界（`renderer.cache.max-entries`），LRU 淘汰最久未使用条目。
- 所有缓存操作并发安全；并发请求同一文档得到一致结果。
- 命中缓存返回的 AST/TOC 是**共享只读视图**，调用方不得修改。

## 资源限制

| 限制 | 配置项 | 默认 | 超限拒绝原因 |
|---|---|---|---|
| 输入长度 | `renderer.limits.max-input-length` | 1000000 | `INPUT_TOO_LONG` |
| 嵌套深度 | `renderer.limits.max-nesting-depth` | 64 | `NESTING_TOO_DEEP` |
| 表格行数 | `renderer.limits.max-table-rows` | 200 | `TABLE_TOO_LARGE` |
| 表格列数 | `renderer.limits.max-table-columns` | 50 | `TABLE_TOO_LARGE` |
| 图片数量 | `renderer.limits.max-images` | 100 | `TOO_MANY_IMAGES` |
| 处理时长 | `renderer.limits.max-processing-millis` | 3000 | `PROCESSING_TIMEOUT` |

超限时返回 `REJECTED_LIMIT` 与上述可区分原因，**不产生部分输出，不污染
缓存**；修复输入后重试可正常成功。

## 渲染选项（版本化）

选项按 schema 版本声明（当前 `V1`）。**新增选项的默认值必须保持既有输出
不变**，因此旧版本请求的行为永远稳定。

| 键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `toc` | boolean | true | 是否输出目录 |
| `headingNumbering` | boolean | true | 是否输出标题编号 |
| `allowRawHtml` | boolean | true | true=净化后放行原始 HTML；false=全部转义 |

- 未知选项按请求的 `unknownPolicy` 处理：`REJECT`（请求被拒绝，
  `REJECTED_OPTIONS`）或 `IGNORE`（忽略并继续）。
- 已知选项的类型错误**始终**被拒绝，与策略无关。
- 选项应用、忽略、拒绝与内部异常都会写入 `OptionsAuditLog` 审计日志。

## REST API

`POST /api/render`

```json
{
  "content": "# Title\n\ntext with [link](https://example.com)",
  "options": {"version": 1, "values": {"toc": true}, "unknownPolicy": "IGNORE"},
  "reservedAnchors": ["intro"]
}
```

响应（200）：

```json
{
  "status": "OK",
  "html": "<nav class=\"toc\">...</nav><h1 id=\"title\">...</h1>",
  "toc": {"number": "", "title": "", "anchor": "", "level": 0, "children": [...]},
  "document": {"type": "DOCUMENT", "children": [...]},
  "errors": [{"severity": "WARNING", "line": 3, "column": 1,
              "message": "...", "skippedReason": "..."}],
  "rejectionReason": null,
  "message": null
}
```

状态码：`200` 成功；`400` 选项被拒绝；`422` 资源超限；`500` 内部失败。

## 构建与测试

```bash
mvn compile   # 编译
mvn test      # 全部 95 个测试
mvn spring-boot:run  # 启动服务（默认 8080 端口）
```

测试覆盖：解析结构（11）、净化绕过（20）、标题冲突（9）、并发缓存（9）、
资源上限（9）、错误恢复（10）、选项兼容（11）、渲染集成（10）、REST（5）。
