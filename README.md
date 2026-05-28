# GodlikeAgents

> 企业级 AI Agent 开发底座

基于 **Java 17 + Spring Boot 4.0.5 + Spring Security 6.x** 构建的 Agent 应用基础框架，提供从推理引擎到权限管理的完整基础设施，让开发者专注于 Agent 业务逻辑而非底层架构。

## 为什么选择 GodlikeAgents

构建企业级 Agent 应用需要处理大量非功能性需求：认证鉴权、多租户隔离、状态管理、工具调用、记忆持久化、审批流程……GodlikeAgents 将这些能力封装为开箱即用的框架组件，通过声明式配置和可扩展接口快速搭建生产级 Agent 应用。

## 核心能力

### Agent 推理引擎

- **状态图引擎** — 基于 [langgraph4j](https://github.com/langchain4j/langgraph4j) 的有向图执行模型，`plan → execute → tool/delegate → finalize` 全生命周期管理
- **工具调用** — `ToolProvider` SPI 接口，内置网络搜索（SearXNG）、代码沙箱执行，支持自定义扩展
- **子 Agent 委派** — 多层嵌套委派，独立沙箱隔离，递归构建子图
- **Human-in-the-Loop** — 声明式中断机制，SSE 实时推送，支持审批/修改/驳回
- **中间件管道** — 洋葱模型 before/after 钩子，可插拔扩展（日志、压缩、记忆注入）

### 长期记忆

- **事实提取** — 对话结束后自动提取用户偏好、客观事实、重要决策、操作流程
- **语义检索** — 智谱 Embedding-3 向量化 + pgvector 相似度搜索，跨会话召回相关记忆
- **多信号融合** — 向量相似度 + 关键词检索双通道，结果去重合并
- **租户隔离** — `tenant_id + user_id` 双维度隔离，符合企业级数据安全要求

### 技能系统

- **热加载** — 目录下的 `SKILL.md` 文件自动识别为技能，无需重启
- **指令注入** — `SkillsMiddleware` 在 plan 节点前动态增强 Agent 指令
- **多技能包** — 通过 Spring Profile 切换技能目录，适配不同业务场景

### 企业级基础

- **多租户** — 数据库行级隔离（`TenantLineInnerInterceptor`）+ `@IgnoreTenant` 跳过注解
- **RBAC + 数据权限** — 角色/菜单/部门三级权限，5 种数据范围（ALL / DEPT_AND_SUB / DEPT / SELF / CUSTOM）
- **认证体系** — JWT / Redis Token 双模式，密码 + 短信 + 微信小程序三种认证方式
- **文件存储** — MinIO / 阿里云 OSS / 本地存储策略模式切换
- **代码生成器** — Velocity 模板（60+ 文件），一键生成前后端 CRUD

## 技术栈

| 依赖 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 4.0.5 | 应用框架 |
| Spring Security | 6.x | 认证授权 |
| MyBatis-Plus | 3.5.15 | ORM / 多租户拦截 |
| PostgreSQL + pgvector | 16.4 | 数据库 + 向量检索 |
| Redis | 7.2.3 | 缓存 / 会话 |
| LangChain4j | 1.0.0 | LLM 集成 |
| langgraph4j | 1.8.16 | Agent 状态图引擎 |
| 智谱 AI SDK | 0.3.3 | Embedding-3 向量化 |
| MapStruct | 1.6.3 | 对象映射 |
| Knife4j | 4.5.0 | API 文档 |
| MinIO | 8.5.10 | 对象存储 |

## 架构

### Agent 引擎执行流

```
用户消息 → DeepAgentGraph
  ├─ plan    → MemoryMiddleware.before(检索记忆) + SkillsMiddleware(注入技能)
  ├─ execute → 路由决策：工具调用 / 子 Agent 委派
  ├─ tool    → ToolProvider 执行
  ├─ delegate → 递归构建子 Agent（独立状态图）
  └─ finalize → MemoryMiddleware.after(提取事实并持久化)
```

### 模块结构

```
src/main/java/com/hypersense/boot/
├── auth/           # 认证：JWT、短信、微信小程序
├── system/         # 业务：用户、角色、菜单、部门、字典、租户
├── framework/
│   ├── agents/     # Agent 推理引擎（核心）
│   │   ├── config/     # 声明式配置属性
│   │   ├── engine/     # 图节点 + 路由决策
│   │   ├── memory/     # 长期记忆（Embedding + pgvector）
│   │   ├── middleware/  # 中间件管道（日志/压缩/记忆）
│   │   ├── sandbox/    # 代码沙箱（本地/远程/容器）
│   │   ├── skill/      # 技能系统（SKILL.md 热加载）
│   │   └── tool/       # 工具提供者 SPI
│   ├── security/   # Security 过滤器链
│   ├── tenant/     # 多租户拦截器
│   └── file/       # 文件存储策略
├── codegen/        # 代码生成器
├── file/           # 文件管理
└── message/        # SSE 实时消息
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- Docker & Docker Compose

### 1. 启动基础设施

```bash
docker-compose -f docker/docker-compose.yml -p godlikeagents up -d
```

| 服务 | 端口 | 说明 |
|------|------|------|
| PostgreSQL (pgvector) | 5432 | 数据库 + 向量检索 |
| Redis | 6379 | 缓存 / 会话 |
| MinIO | 9000 / 9001 | 对象存储 |
| XXL-Job | 8080 | 任务调度 |
| SearXNG | 8888 | 元搜索引擎 |

### 2. 配置

编辑 `src/main/resources/application-dev.yml`，通过环境变量注入敏感配置：

```yaml
spring:
  datasource:
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

agent:
  llm:
    openai:
      api-key: ${ZHIPU_API_KEY}
  memory:
    enabled: true   # 启用长期记忆
```

### 3. 构建运行

```bash
mvn clean package
java -jar target/godlikeagents.jar
```

- API 文档：`http://localhost:8000/doc.html`

## License

[Apache License 2.0](LICENSE)
