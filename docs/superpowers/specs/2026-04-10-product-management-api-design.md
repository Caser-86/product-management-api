# 商品管理 API 设计文档

- 日期: 2026-04-10
- 主题: 电商系统商品管理 API
- 状态: Draft
- 适用范围: 混合平台模式（平台自营 + 第三方商家）

## 1. 背景与目标

本文档定义电商系统商品管理域的 API 与数据模型设计，覆盖以下能力：

- 商品 CRUD
- 商品搜索与筛选
- 库存管理
- 价格变更历史

本文档基于以下已确认前提：

- 平台为混合模式，同时支持平台自营商品和第三方商家商品
- 商品建模采用 SPU + SKU 模式
- 查询面向两类用户：
  - 管理端商品查询与筛选
  - 前台商城商品搜索与浏览
- 库存采用混合一致性策略：
  - 核心写路径强一致
  - 搜索、缓存、报表允许短暂最终一致
- 价格能力同时支持：
  - 人工改价审计
  - 未来时间生效的价格计划

目标是给出一套可扩展、易审计、便于后续实现的商品管理方案。

非目标：

- 不包含订单、支付、营销活动引擎的完整设计
- 不包含商家入驻、结算、售后等域模型
- 不在本文内展开前端页面交互细节

## 2. 总体设计原则

- 以 SKU 作为库存和价格的核心管理单元
- 以 SPU 作为商品展示和管理聚合单元
- 管理端查询与前台搜索分离，避免交易库承担高并发检索
- 当前状态与历史流水分离，保证读性能与可审计性
- 多租户边界明确，所有核心业务数据均带 `merchant_id`
- 写路径优先保证一致性，读路径优先保证性能和可扩展性

## 3. 业务对象与边界

### 3.1 业务对象

- Merchant: 商家，包含平台自营和第三方商家
- SPU: 商品定义，例如“男士连帽卫衣”
- SKU: 可售卖单元，例如“黑色 L”
- Inventory: SKU 库存余额和库存流水
- Price: SKU 当前价格、价格历史、价格计划
- Search Document: 面向前台搜索和后台复杂筛选的读模型

### 3.2 多租户边界

- 平台自营商品与第三方商品统一建模
- 平台自营使用平台商家身份管理，不保留“无商家商品”
- 商家用户只能访问自己 `merchant_id` 下的数据
- 平台运营角色可以跨商家访问并执行受控操作

## 4. 技术方案选择

推荐方案为“模块化单体 + 关系型数据库 + Redis + 搜索引擎 + 事件总线”。

推荐组合：

- 应用层: Spring Boot 或 NestJS
- 主数据库: MySQL 8 或 PostgreSQL
- 缓存: Redis
- 搜索: Elasticsearch 或 OpenSearch
- 异步事件: Kafka 或 RabbitMQ

推荐理由：

- 相比纯单体，读写职责更清晰，后续更容易演进
- 相比一开始微服务化，复杂度更可控
- 能较好支撑商品查询、库存一致性、价格历史与搜索同步

## 5. 架构概览

系统建议拆分为以下逻辑模块：

- Product Module
  - 管理 SPU、SKU、图片、属性、上下架
- Inventory Module
  - 管理库存余额、预占、确认、释放、人工调整
- Pricing Module
  - 管理当前价格、价格历史、未来生效价格
- Search Projection Module
  - 将商品、价格、库存聚合后投影到搜索索引
- Auth/RBAC Module
  - 提供平台运营与商家权限控制

职责关系：

- Product Module 负责商品主数据
- Inventory Module 与 Pricing Module 只依赖 SKU，不直接依赖前台搜索
- Search Projection Module 消费变更事件生成读模型

## 6. 关键状态设计

### 6.1 SPU 状态

SPU 建议拆分为 3 组状态：

- `status`: `draft | active | inactive | deleted`
- `audit_status`: `pending | approved | rejected`
- `publish_status`: `unpublished | published`

前台可见条件：

- `status = active`
- `audit_status = approved`
- `publish_status = published`

### 6.2 SKU 状态

SKU 建议拆分为 2 组状态：

- `status`: `active | inactive | deleted`
- `sale_status`: `sellable | unsellable`

SKU 可售条件：

- 所属 SPU 满足前台可见条件
- SKU `status = active`
- SKU `sale_status = sellable`

### 6.3 库存动作

库存采用动作驱动而不是单一状态：

- `adjust`: 人工增减库存
- `reserve`: 预占库存
- `confirm`: 确认扣减
- `release`: 释放预占
- `refund`: 退货回补

### 6.4 价格状态

- 当前有效价格保存在 `price_current`
- 未来价格计划保存在 `price_schedule`
- 所有价格变化写入 `price_history`

## 7. API 设计

### 7.1 管理端商品 API

- `POST /admin/products`
- `GET /admin/products`
- `GET /admin/products/{productId}`
- `PATCH /admin/products/{productId}`
- `DELETE /admin/products/{productId}`
- `POST /admin/products/{productId}/publish`
- `POST /admin/products/{productId}/unpublish`

### 7.2 管理端 SKU API

- `POST /admin/products/{productId}/skus`
- `PATCH /admin/skus/{skuId}`
- `DELETE /admin/skus/{skuId}`

### 7.3 库存 API

- `GET /admin/skus/{skuId}/inventory`
- `POST /admin/skus/{skuId}/inventory/adjust`
- `POST /inventory/reservations`
- `POST /inventory/reservations/{reservationId}/confirm`
- `POST /inventory/reservations/{reservationId}/release`
- `GET /admin/skus/{skuId}/inventory/history`

### 7.4 价格 API

- `GET /admin/skus/{skuId}/prices`
- `PATCH /admin/skus/{skuId}/prices`
- `POST /admin/skus/{skuId}/price-schedules`
- `GET /admin/skus/{skuId}/price-history`
- `POST /admin/price-schedules/{scheduleId}/cancel`

### 7.5 前台检索 API

- `GET /products`
- `GET /products/{productId}`
- `GET /products/{productId}/skus`
- `GET /categories/{categoryId}/products`
- `GET /brands/{brandId}/products`

### 7.6 设计约束

- 管理端接口和前台接口分离
- 价格、库存等高风险写接口必须记录审计信息
- 库存预占与价格计划接口应支持幂等控制
- 列表接口必须分页

## 8. 数据模型设计

### 8.1 核心表

- `merchant`
- `brand`
- `category`
- `attribute_definition`
- `product_spu`
- `product_spu_category`
- `product_spu_image`
- `product_spu_attribute_value`
- `product_sku`
- `product_sku_attribute_value`
- `inventory_balance`
- `inventory_ledger`
- `price_current`
- `price_history`
- `price_schedule`
- `product_audit_log`

### 8.2 核心设计说明

#### `product_spu`

表示商品定义层，承载：

- 标题、副标题、描述
- 品牌
- 主分类
- 审核与上下架状态

#### `product_sku`

表示可售卖单元，承载：

- SKU 编码与条码
- 规格快照
- 状态与可售状态
- 与库存、价格的关联

建议额外维护 `spec_hash` 字段，基于排序后的规格值计算，用于保证同一 SPU 下规格组合唯一。

#### `inventory_balance`

表示当前库存快照，承载：

- `total_qty`
- `available_qty`
- `reserved_qty`
- `sold_qty`
- `version`

#### `inventory_ledger`

表示库存流水，用于审计与对账。

#### `price_current`

表示当前有效价，便于高频读取。

#### `price_history`

记录旧价格、新价格、操作人、原因和变更时间。

#### `price_schedule`

表示未来计划生效的价格版本。

### 8.3 ER 关系

主要关系如下：

- 一个 `merchant` 拥有多个 `product_spu`
- 一个 `product_spu` 包含多个 `product_sku`
- 一个 `product_sku` 对应一个 `inventory_balance`
- 一个 `product_sku` 对应一个 `price_current`
- 一个 `product_sku` 对应多条 `inventory_ledger`
- 一个 `product_sku` 对应多条 `price_history`
- 一个 `product_sku` 对应多条 `price_schedule`

## 9. 关键索引与约束

### 9.1 唯一性约束

- `merchant.merchant_code` 唯一
- `brand.name` 视业务决定是否唯一
- `product_spu.spu_code` 唯一
- `product_sku.sku_code` 唯一
- `product_sku.barcode` 可选唯一
- `product_sku(spu_id, spec_hash)` 唯一

### 9.2 常用索引

- `product_spu(merchant_id, status)`
- `product_spu(merchant_id, category_id, publish_status, status)`
- `product_sku(spu_id)`
- `product_sku(merchant_id, status, sale_status)`
- `inventory_ledger(sku_id, created_at)`
- `price_history(sku_id, created_at)`
- `price_schedule(status, effective_at)`

### 9.3 数据约束

- 价格不能为负数
- `list_price >= sale_price`
- 库存余额不能出现负数
- 所有多租户访问均需进行 `merchant_id` 范围校验

## 10. 搜索与筛选设计

### 10.1 查询职责拆分

管理端查询目标：

- 支持商家、状态、分类、审核状态、上下架状态、库存范围等条件筛选

前台搜索目标：

- 支持关键词搜索
- 支持分类、品牌、属性筛选
- 支持价格区间与排序

### 10.2 搜索实现建议

- 前台搜索优先使用 Elasticsearch/OpenSearch
- 后台简单查询可走主库，复杂筛选可复用搜索索引
- 搜索文档由主库数据异步投影生成

搜索文档应聚合以下信息：

- SPU 基本信息
- SKU 价格区间
- 库存可售状态
- 销量与排序字段
- 属性聚合信息

### 10.3 缓存建议

- 商品详情缓存
- 分类页商品列表缓存
- 品牌页缓存
- 热门搜索和筛选缓存

缓存失效由商品、价格、库存变更事件驱动。

## 11. 库存设计

### 11.1 一致性策略

库存采用混合一致性策略：

- 写路径强一致
- 搜索与缓存允许最终一致

### 11.2 库存流程

下单预占：

- 校验可售库存
- 原子扣减 `available_qty`
- 原子增加 `reserved_qty`
- 记录 `reserve` 流水

支付成功：

- 原子扣减 `reserved_qty`
- 原子增加 `sold_qty`
- 记录 `confirm` 流水

订单取消：

- 原子扣减 `reserved_qty`
- 原子回补 `available_qty`
- 记录 `release` 流水

人工调库存：

- 更新快照表
- 记录 `adjust` 流水

### 11.3 并发控制

- 优先使用单 SQL 原子更新或乐观锁
- 写接口使用幂等键避免重复预占
- 防止超卖是库存服务的一等约束

## 12. 价格设计

### 12.1 当前价格与历史

- 当前价格放在 `price_current`
- 每次改价写 `price_history`

### 12.2 未来生效价格

通过 `price_schedule` 支持未来价格计划：

- 创建计划时写入待生效价格
- 定时任务扫描到期计划
- 生效时更新 `price_current`
- 同时写 `price_history`
- 更新计划状态为 `executed`

### 12.3 价格约束

- 即时改价与计划改价均需校验价格合法性
- 应支持操作原因和操作人审计
- 对未生效计划可取消，对已执行计划不可回退为未执行

## 13. 事件与数据流

### 13.1 商品变更同步搜索

推荐采用 Outbox/Event Bus 模式：

- 商品服务更新主库并写出站事件
- 异步消费者聚合商品、价格、库存数据
- 更新搜索索引
- 删除或刷新缓存

### 13.2 核心事件

- `ProductChanged`
- `SkuChanged`
- `InventoryChanged`
- `PriceChanged`
- `ProductPublished`
- `ProductUnpublished`

## 14. 安全与审计

### 14.1 权限控制

- 管理端使用 RBAC
- 商家与平台运营的权限边界必须显式区分
- 风险操作需细粒度权限控制：
  - 改价
  - 调库存
  - 上下架
  - 删除商品

### 14.2 数据隔离

- 服务端不能只信任前端传入的 `merchant_id`
- 所有查询与更新必须根据认证上下文注入租户边界

### 14.3 审计

以下操作必须审计：

- 创建商品
- 修改商品
- 创建或修改 SKU
- 上下架
- 改库存
- 改价格
- 创建或取消价格计划

推荐使用 `product_audit_log`、`inventory_ledger`、`price_history` 三类记录覆盖主审计链路。

## 15. 性能设计

### 15.1 数据库层

- 高频列表查询走合适联合索引
- 历史表和流水表建议后续按时间分区或归档
- 避免在交易主表上执行复杂模糊搜索和深分页

### 15.2 搜索层

- 前台搜索走独立搜索引擎
- 深分页优先使用搜索引擎的游标能力而不是数据库 offset

### 15.3 缓存层

- 商品详情缓存
- 热门商品和分类页缓存
- 使用事件驱动失效
- 处理缓存击穿、穿透与热点键问题

## 16. 错误码与返回规范

统一响应结构：

- `success`
- `code`
- `message`
- `traceId`
- `data`
- `error.details`

建议错误码分组：

- 通用: `COMMON_*`
- 认证授权: `AUTH_*`
- 商品: `PRODUCT_*`
- SKU: `SKU_*`
- 库存: `INVENTORY_*`
- 价格: `PRICE_*`
- 搜索: `SEARCH_*`

关键错误码示例：

- `PRODUCT_NOT_FOUND`
- `SKU_SPEC_DUPLICATED`
- `INVENTORY_INSUFFICIENT`
- `INVENTORY_VERSION_CONFLICT`
- `PRICE_SALE_GREATER_THAN_LIST`
- `PRICE_SCHEDULE_CONFLICT`
- `AUTH_MERCHANT_SCOPE_DENIED`

HTTP 状态码建议：

- `200` 查询或修改成功
- `201` 创建成功
- `400` 请求参数错误
- `401` 未认证
- `403` 无权限
- `404` 资源不存在
- `409` 业务冲突或版本冲突
- `429` 限流
- `500` 服务端内部错误
- `503` 依赖不可用

## 17. 测试策略

### 17.1 单元测试

- 价格合法性校验
- SKU 规格组合唯一性校验
- 状态流转校验
- 错误码映射校验

### 17.2 集成测试

- 商品创建与查询
- SKU 创建与重复规格拦截
- 库存预占、确认、释放
- 即时改价与历史记录
- 未来价格计划生效

### 17.3 并发测试

- 并发预占库存防超卖
- 并发改价的版本冲突控制
- 搜索同步期间前台读请求稳定性

### 17.4 回归测试

- 管理端列表筛选组合
- 前台搜索与筛选结果一致性
- 审计日志完整性

## 18. 分阶段落地建议

### Phase 1: 最小可用版本

实现以下能力：

- 商品 CRUD
- SKU 管理
- 即时库存管理
- 即时改价
- 价格历史
- 基础管理端查询

### Phase 2: 搜索与前台读模型

- 引入搜索引擎
- 建立商品搜索文档
- 打通商品、价格、库存到搜索索引的异步同步

### Phase 3: 计划价格与扩展治理

- 未来价格计划
- 更完善的审计
- 分区归档与性能优化

## 19. 风险与后续决策点

当前设计仍保留以下实现层决策待后续计划阶段明确：

- 具体技术栈采用 Spring Boot 还是 NestJS
- 搜索索引是否同时承担后台复杂筛选
- 幂等键与 reservation 的存储位置
- 是否在第一期引入消息队列和 Outbox
- 是否需要支持多币种定价

这些决策不影响当前领域模型和 API 边界，可以在实现计划阶段定稿。

## 20. 结论

本方案以 SPU + SKU 为中心，围绕库存强一致写入、价格可审计、搜索读写分离和多租户权限隔离构建。它适合中大型电商平台的商品管理场景，并为后续服务拆分、性能扩展和审计治理预留了清晰边界。

下一步应在本设计确认后进入实现计划阶段，补充：

- 模块划分
- 接口实现顺序
- 数据迁移与初始化策略
- 测试与验收计划
