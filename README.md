# GodlikeAgents v1.0.0

基于 **Java 17 + Spring Boot 4.0.5 + Spring Security 6.x** 的多租户权限管理系统，内置 AI Agent 深度推理引擎。

## 特性

### 核心框架

- **多租户权限管理** — 数据库行级隔离（`tenant_id` + MyBatis-Plus 拦截器），RBAC 角色权限，部门数据权限（5 种数据范围）
- **Spring Security 6.x** — JWT / Redis Token 双会话模式，密码认证 + 短信认证 + 微信小程序登录
- **代码生成器** — Velocity 模板（60+ `.vm` 文件），一键生成前后端 CRUD

### AI Agent 引擎

- **深度推理图** — 基于 [langgraph4j](https://github.com/langchain4j/langchain4j) 的状态图引擎：`plan → execute → tool/delegate → plan → ... → finalize`
- **工具调用** — 可扩展的 `ToolProvider` 接口，内置网络搜索（SearXNG）、代码沙箱执行
- **HITL 审批** — Human-in-the-Loop 中断机制，支持 SSE 实时交互
- **技能系统** — `SKILL.md` 热加载，通过 `SkillsMiddleware` 动态注入 Agent 指令
- **长期记忆** — 智谱 Embedding-3 向量化 + pgvector 语义检索，跨会话记忆用户偏好、事实、决策
- **消息压缩** — 自动压缩长对话上下文，大输出卸载到 Redis
- **子 Agent 委派** — 支持多层嵌套委派，独立沙箱隔离

### 基础设施

- **文件存储** — MinIO / 阿里云 OSS / 本地存储三策略（策略模式）
- **SSE 实时消息** — Server-Sent Events 推送 Agent 执行事件
- **沙箱执行** — 本地子进程 / 远程云沙箱 / Docker 容器三种模式
- **操作审计** — `@Log` 注解声明式日志记录

## 技术栈

| 依赖 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 4.0.5 | 应用框架 |
| Spring Security | 6.x | 认证授权 |
| MyBatis-Plus | 3.5.15 | ORM / 分页 / 逻辑删除 / 多租户拦截 |
| PostgreSQL + pgvector | 16.4 | 数据库 + 向量检索 |
| Redis | 7.2.3 | 缓存 / 会话 / Token |
| LangChain4j | 1.0.0 | LLM 集成 |
| langgraph4j | 1.8.16 | Agent 状态图引擎 |
| 智谱 AI SDK | 0.3.3 | Embedding-3 向量化 |
| MapStruct | 1.6.3 | 对象映射 |
| Knife4j | 4.5.0 | API 文档 |
| Hutool | 5.8.41 | 工具库 |
| MinIO | 8.5.10 | 对象存储 |
| XXL-Job | 3.2.0 | 定时任务 |

## 架构

```
Controller → Service → Mapper (MyBatis-Plus) → PostgreSQL
   ↓            ↓           ↓
 Form/Query   Entity/VO   XML Mapper
```

### Agent 引擎架构

```
用户消息 → DeepAgentGraph
  ├─ plan 节点 → MemoryMiddleware.before(检索记忆) + SkillsMiddleware(注入技能)
  ├─ execute 节点 → 工具选择 / 子 Agent 委派
  ├─ tool 节点 → ToolProvider 执行
  ├─ delegate 节点 → 递归构建子 Agent
  └─ finalize 节点 → MemoryMiddleware.after(提取事实并存储)
```

### 模块结构

```
src/main/java/com/hypersense/boot/
├── auth/           # 认证：JWT、短信、微信小程序
├── system/         # 核心：用户、角色、菜单、部门、字典、租户
├── framework/
│   ├── agents/     # AI Agent 引擎
│   │   ├── config/     # 配置属性
│   │   ├── engine/     # 图节点 + 路由
│   │   ├── memory/     # 长期记忆（Embedding + pgvector）
│   │   ├── middleware/  # 中间件管道
│   │   ├── sandbox/    # 代码沙箱
│   │   ├── skill/      # 技能系统
│   │   └── tool/       # 工具提供者
│   ├── security/   # Security 配置、过滤器链
│   ├── tenant/     # 多租户拦截
│   └── file/       # 文件存储
├── codegen/        # 代码生成器
├── file/           # 文件管理
└── message/        # SSE 实时消息
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- Docker & Docker Compose
- PostgreSQL 16+ (with pgvector)

### 1. 启动基础设施

```bash
docker-compose -f docker/docker-compose.yml -p godlikeagents up -d
```

| 服务 | 端口 | 说明 |
|------|------|------|
| PostgreSQL (pgvector) | 5432 | 数据库 |
| Redis | 6379 | 缓存 |
| MinIO | 9000 / 9001 | 对象存储 |
| XXL-Job Admin | 8080 | 任务调度 |
| SearXNG | 8888 | 元搜索引擎 |

### 2. 配置

编辑 `src/main/resources/application-dev.yml`，填入实际值：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/godlikeagents
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

agent:
  llm:
    openai:
      api-key: ${ZHIPU_API_KEY}  # 智谱 API Key
  memory:
    enabled: true  # 启用长期记忆（需 pgvector）
```

### 3. 构建运行

```bash
mvn clean package
java -jar target/godlikeagents.jar
```

应用启动后访问：
- API 文档：`http://localhost:8000/doc.html`
- MinIO 控制台：`http://localhost:9001`

## 长期记忆系统

基于 **智谱 Embedding-3 + pgvector** 的跨会话记忆：

```
对话完成 → LLM 提取事实 → Embedding-3 向量化 → pgvector 存储
                                                    ↓
新对话开始 → 语义检索相关记忆 → 注入 Agent 上下文 → 更精准的响应
```

支持的记忆类型：
- **preference** — 用户偏好（格式、风格、工具选择）
- **fact** — 客观事实（项目信息、技术栈）
- **decision** — 重要决策（架构选择、方案取舍）
- **procedure** — 操作流程（工作方式、步骤）

## License

[Apache License 2.0](LICENSE)
