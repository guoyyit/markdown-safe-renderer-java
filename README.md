# markdown-safe-renderer-java

面向并发请求的文档渲染与受限 HTML 净化服务（Spring Boot 3 / Java 17）。
它接收**不可信**的文档内容，返回可遍历的结构化 AST、稳定的标题编号/锚点、
目录以及净化后的受限 HTML，并在输入不完整或恶意时保持**确定且可恢复**的行为。

## 快速开始

```bash
mvn spring-boot:run
```

```bash
curl -X POST http://localhost:8080/api/render \
  -H 'Content-Type: application/json' \
  -d '{"content":"# Title\n\nHello [ex](https://example.com)","options":{}}'
```

返回：

```json
{
  "document": { "...": "immutable AST (type / position / children / literal / attributes)" },
  "html": "<h1 id=\"3f2a1d7b-title\">...</h1>...",
  "toc":  [{ "number":"1", "title":"1 Title", "anchor":"3f2a1d7b-title", "level":1, "children":[] }],
  "tocHtml": "<ul class=\"toc\">...</ul>",
  "errors": []
}
```

## HTTP 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/render` | 渲染文档，body 为 `{ "content": "...", "options": { ... } }` |
| GET  | `/api/render/audit` | 查看审计事件（选项切换、未知选项、拒绝、错误） |
| DELETE | `/api/render/cache` | 清空结果缓存 |

资源超限返回可区分的状态码与原因：

| reason | HTTP |
| --- | --- |
| `INPUT_TOO_LONG` | 413 |
| `PROCESSING_TIMEOUT` | 408 |
| `NESTING_TOO_DEEP` / `TABLE_TOO_LARGE` / `TOO_MANY_IMAGES` / `INTERNAL_RESOURCE_LIMIT` | 422 |
| `INVALID_OPTIONS` | 400 |

## 支持的文档结构

- ATX 标题（`#`–`######`）、段落、分隔线
- 有序 / 无序列表（含同文档内嵌套，保留 `start`）
- 管道表格（对齐符 `:--`、`--:`、`:--:`，单元格支持对齐属性）
- 围栏代码块（```` ``` ```` / `~~~`，info string 只允许安全字符）与缩进代码块
- 引用块（可嵌套）
- 行内链接、自动链接、行内代码
- 图片（受数量上限与协议白名单约束）
- 原始 HTML（默认整体转义；`rawHtmlAllowed=true` 时走净化白名单）

每个节点携带 **1-based `SourcePos(line, column)`**、`type`、`children`、`literal`、
`attributes`，节点 freeze 后不可变，可安全地被并发下游持有与遍历（`NodeVisitor`）。

## 安全模型（务必阅读 SECURITY.md）

服务不依赖第三方 HTML 清洗库，净化是**白名单 + 重新序列化**模型：

1. **标签白名单**：仅 `SanitizePolicy.defaultPolicy()` 允许的标签输出为标签，
   其余原样转义为惰性文本；`script/style/iframe/object/embed` 的开标签及其
   文本内容被整体丢弃（直到匹配闭合标签，嵌套同名标签不会提前逃逸）。
2. **属性白名单**：非允许属性一律丢弃，因此所有 `on*` 事件属性、`style` 等
   天然不存在；属性值输出时重新转义，值无法突破引号边界。
3. **危险协议拦截**：`href/src/cite/action/formaction` 先做实体解码、去控制
   字符/空白，再按协议白名单（默认仅 `http/https/mailto` 及相对/锚点引用）
   大小写不敏感校验。`javascript:`、`vbscript:`、`data:text/html` 以及
   `&#106;`、`&#x6a;`、制表/换行混淆均无法通过。
4. **大小写与嵌套变形**：标签名/属性名/协议统一小写匹配；每个 token 独立
   校验，注释/DOCTYPE/PI 被删除，畸形标签（如未闭合引号）整体转义，无法
   借嵌套或大小写夹带属性。
5. **Markdown 自身的链接/图片** 同样经过协议校验：危险链接退化为纯文本，
   危险图片渲染为占位 `<span>`；代码块/行内代码内容一律 HTML 转义。
6. **默认原始 HTML 不信任**：即使作者在 markdown 中写入 `<script>`，默认
   也只输出转义后的文本；管理员显式打开 `rawHtmlAllowed` 后仍强制走白名单。

## 标题编号、锚点与目录

- 编号按真实层级递增：`1` → `1.1` → `1.2`，缺失层级保留实际层级（不会
  把 h4 压成 h2）；相同输入永远得到相同编号。
- 锚点 = `文档内容短哈希 + "-" + slug(标题)`，配合文档内计数器：
  重复标题依次得到 `-2`、`-3`；**不同文档**即使标题完全相同也不会冲突，
  因此锚点全局唯一且确定。
- 目录中的每个 `#anchor` 与正文渲染出的 `id` 一一对应；`tocHtml` 与
  结构化 `toc` 一致。

## 缓存语义

- Key = `SHA-256(规范化选项 | 输入长度 | 输入内容)`：**输入变化或选项变化
  必然 miss**；旧输入的缓存条目自然不会被复用（不主动覆盖，靠容量/TTL
  淘汰，见 `DELETE /api/render/cache` 手动失效）。
- 只有**完整成功**的 `RenderResult` 才会 `put`；异常、超时、资源耗尽路径
  不写缓存，因此**永远不会读到半初始化结果，缓存不会被失败污染**。
- 并发相同请求通过 in-flight `CompletableFuture` 合并为一次计算，每个
  等待者保留独立超时；结果对象不可变，锁外读取安全。
- 容量内 LRU（访问序）+ 每条目 TTL，过期条目惰性清除；容量与 TTL 可配置。

## 资源限制（返回可区分拒绝原因，不产生部分输出）

| 配置 | 默认值 | 超限原因 |
| --- | --- | --- |
| `renderer.max-input-chars` | 1000000 | `INPUT_TOO_LONG` |
| `renderer.max-depth` | 64 | `NESTING_TOO_DEEP` |
| `renderer.max-table-rows` | 1000 | `TABLE_TOO_LARGE` |
| `renderer.max-table-cols` | 100 | `TABLE_TOO_LARGE` |
| `renderer.max-images` | 100 | `TOO_MANY_IMAGES` |
| `renderer.timeout-millis` | 5000 | `PROCESSING_TIMEOUT` |

解析在工作线程中执行，超时 `Future.cancel(true)` 协作中断；超限抛
`LimitExceededException`，本次不产出、不入缓存；修复输入后重试即可成功。

## 错误恢复

未闭合围栏/行内代码、列数不匹配的表格行、表头/分隔行列数不一致、未知
结构都**不会**让整篇失败：对应构造被跳过或降级（坏行跳过、坏表降级为
段落、未闭合代码变文本），并在 `errors[]` 中给出 `{line:column, reason,
skippedFragment}`；其余内容继续正常渲染。修复输入后重渲染无错误。

## 选项版本化与审计

`RenderOptions`（schema v1）：

| 选项 | 默认 | 含义 |
| --- | --- | --- |
| `schemaVersion` | 1 | 低于 1 或高于当前支持版本 → 400 |
| `headingNumbers` | true | 显示标题编号 |
| `generateToc` | true | 返回 `tocHtml` |
| `sanitizeHtml` | true | 净化原始 HTML |
| `rawHtmlAllowed` | false | 是否允许原始 HTML 标签进入（仍强制白名单） |
| `unknownOptionHandling` | `REJECT` | 未知选项 `REJECT`(400) 或 `IGNORE` |

新增选项在新版本必须取**保持旧输出**的默认值；未知键默认拒绝（前向兼容
防意外），也可配置为忽略。选项切换、未知选项、渲染拒绝、内部错误都写入
`AuditLog`，可通过 `GET /api/render/audit` 查看。

## 项目结构

```
ast/       不可变文档节点、位置、访问者
parser/    块级 + 行内解析，恢复性错误收集，深度/表格/中断检查
sanitize/  白名单策略与手写状态机式 HTML 净化
render/    AST → HTML、标题编号/锚点、目录
cache/     容量 + TTL 的并发安全 LRU
limits/    资源限制与可区分拒绝原因
options/   版本化选项、未知选项处理、切换审计
audit/     有界并发安全审计日志
service/   校验 → 缓存 → 合并 → 超时执行 → 资源计数
web/       REST 入口与异常映射
config/    属性绑定与 Bean 装配
```

## 测试

```bash
mvn test
```

覆盖：解析结构与位置、净化绕过（脚本/事件/协议混淆/引号逃逸/注释/嵌套）、
标题冲突与跨文档锚点、并发缓存合并与失效、资源上限与超时不污染缓存、
局部错误恢复与重试、选项版本兼容/未知选项/审计、HTTP 状态映射，共 60+ 用例。
