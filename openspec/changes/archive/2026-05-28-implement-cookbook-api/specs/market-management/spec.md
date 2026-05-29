## ADDED Requirements

### Requirement: 新增菜单分类
系统SHALL支持新增菜单分类，包含分类名称和图标。

#### Scenario: 成功新增分类
- **WHEN** 客户端发送POST `/api/market/addCategory`，body包含`{name, image}`
- **THEN** 系统创建新Market文档，foods初始化为空数组，返回`{code: 200, message: 'success', data: <Market对象>}`

#### Scenario: 分类名重复
- **WHEN** 分类名称已存在
- **THEN** 系统返回409状态码，`{statusCode: 409, timestamp, path, message: '分类名称已存在'}`

### Requirement: 删除菜单分类
系统SHALL支持删除整个分类及其关联的图片文件。

#### Scenario: 成功删除分类
- **WHEN** 客户端发送DELETE `/api/market/deleteCategory?id=<分类id>`
- **THEN** 系统删除该分类文档；若该分类有image属性，则删除`static/images/market/`下对应文件；返回`{code: 200, message: 'success', data: '删除成功'}`

#### Scenario: 分类不存在
- **WHEN** 分类id不存在
- **THEN** 系统返回404，`{message: '分类不存在'}`

### Requirement: 编辑分类
系统SHALL支持编辑分类的名称和图片。

#### Scenario: 成功编辑分类
- **WHEN** 客户端发送PUT `/api/market/updateCategory`，body包含`{id, name, image}`
- **THEN** 系统更新该分类的name和image字段，返回`{code: 200, message: 'success', data: <更新后的Market对象>}`

#### Scenario: 编辑时名称重复
- **WHEN** 目标名称已被其他分类使用
- **THEN** 系统返回409，`{message: '分类名称已存在'}`

### Requirement: 添加菜品
系统SHALL支持向指定分类添加菜品。

#### Scenario: 成功添加菜品
- **WHEN** 客户端发送PUT `/api/market/addFood`，body包含`{categoryId, foods: {name, describe, burden, image, num}}`
- **THEN** 系统将foods对象push到目标分类的foods数组，返回`{code: 200, message: 'success', data: '添加成功'}`

### Requirement: 删除菜品
系统SHALL支持从分类中删除指定菜品，可选清理关联图片。

#### Scenario: 成功删除菜品(含图片)
- **WHEN** 客户端发送DELETE `/api/market/deleteFood`，body包含`{categoryId, foodId, image}`
- **THEN** 系统从分类foods数组中pull该菜品，删除磁盘上对应图片文件

#### Scenario: 删除菜品(不含图片)
- **WHEN** body不包含image字段
- **THEN** 系统仅从数组移除菜品，不操作文件系统

### Requirement: 批量更新菜品被点数量
系统SHALL支持批量更新多个菜品的`num`字段(被点次数)。

#### Scenario: 批量增加被点数量
- **WHEN** 客户端发送PUT `/api/market/updateFoodWithNum`，body包含`{foodIds: [id1, id2], increment: 1}`
- **THEN** 系统遍历所有Market文档，将匹配foodIds的菜品num字段增加increment值

### Requirement: 更新菜品(不含图片)
系统SHALL支持更新菜品信息(不含图片更换)，支持跨分类移动。

#### Scenario: 同分类内更新菜品
- **WHEN** body中`categoryId === targetCategoryId`
- **THEN** 系统在同一分类的foods数组中使用位置操作符`$`更新目标菜品字段

#### Scenario: 跨分类移动菜品
- **WHEN** body中`categoryId !== targetCategoryId`
- **THEN** 系统从源分类pull该菜品，将其push到目标分类的foods数组

### Requirement: 更新菜品(含图片)
系统SHALL支持更新菜品信息并替换图片，替换前删除旧图片文件。

#### Scenario: 替换菜品图片
- **WHEN** body包含`oldImage`字段
- **THEN** 系统先删除oldImage对应的磁盘文件，再执行与updateFoodWithoutImage相同的更新逻辑

### Requirement: 查询全部菜单
系统SHALL支持查询所有分类及其包含的全部菜品。

#### Scenario: 获取全部菜单
- **WHEN** 客户端发送GET `/api/market/getAll`
- **THEN** 系统返回所有Market文档列表，`{code: 200, message: 'success', data: [Market数组]}`

### Requirement: 按食材搜索菜品
系统SHALL支持按食材(burden)字段模糊搜索菜品。

#### Scenario: 按食材搜索
- **WHEN** 客户端发送GET `/api/market/findFoods?text=鸡蛋`
- **THEN** 系统使用正则表达式模糊匹配foods数组中的burden字段(大小写不敏感)，返回匹配的Market文档列表

### Requirement: 上传菜品图片
系统SHALL支持上传图片文件，返回可访问的URL路径。

#### Scenario: 上传图片
- **WHEN** 客户端发送POST `/api/market/uploadImage`，multipart/form-data包含`file`字段
- **THEN** 系统将文件保存到`static/images/market/`，文件名为`时间戳 + 原扩展名`，返回`{code: 200, message: 'success', data: '/static/images/market/<filename>'}`

#### Scenario: 文件大小超限
- **WHEN** 上传文件超过10MB
- **THEN** 系统拒绝上传，返回错误响应