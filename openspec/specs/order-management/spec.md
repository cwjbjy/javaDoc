## ADDED Requirements

### Requirement: 新增订单
系统SHALL支持创建新订单，记录日期、菜品数量、菜品详情和创建时间。

#### Scenario: 成功创建订单
- **WHEN** 客户端发送POST `/api/order/addOrder`，body包含`{date, num, foods: [{_id, name, describe, burden, image, value}]}`
- **THEN** 系统创建Order文档，自动设置createdAt为当前时间，返回`{code: 200, message: 'success', data: <Order对象>}`

### Requirement: 分页查询订单
系统SHALL支持按创建时间倒序分页查询订单。

#### Scenario: 分页查询
- **WHEN** 客户端发送GET `/api/order/getOrder?skip=0&pageSize=10`
- **THEN** 系统返回`{code: 200, message: 'success', data: {foods: [订单列表], total: <总记录数>}}`

#### Scenario: 按创建时间倒序
- **WHEN** 查询订单列表
- **THEN** 结果按createdAt降序排列，最新订单在前

### Requirement: 删除订单
系统SHALL支持通过订单id删除订单。

#### Scenario: 成功删除订单
- **WHEN** 客户端发送DELETE `/api/order/deleteOrder`，body包含`{id}`
- **THEN** 系统删除对应Order文档，返回`{code: 200, message: 'success', data: '删除成功'}`