## ADDED Requirements

### Requirement: 图片文件上传
系统SHALL支持通过multipart/form-data上传图片文件，保存到静态资源目录并返回访问URL。

#### Scenario: 上传图片文件
- **WHEN** 客户端POST multipart文件到`/api/market/uploadImage`
- **THEN** 系统保存文件到`static/images/market/`目录，文件名为`System.currentTimeMillis() + 原文件扩展名`，返回图片访问路径`/static/images/market/<filename>`

#### Scenario: 限制文件大小
- **WHEN** 上传文件超过10MB
- **THEN** 系统返回413或400错误

#### Scenario: 静态资源访问
- **WHEN** 客户端GET `/static/images/market/<filename>`
- **THEN** 系统返回对应的图片文件

### Requirement: 删除分类时清理图片
系统SHALL在删除分类时同时删除其关联的图片文件。

#### Scenario: 删除有图片的分类
- **WHEN** 删除一个image字段非空的Market分类
- **THEN** 系统从`static/images/market/`删除该image对应的文件，再删除数据库中的分类文档

### Requirement: 删除菜品时可选清理图片
系统SHALL在删除菜品时，若请求中包含image字段，则删除对应的图片文件。

#### Scenario: 删除含图片的菜品
- **WHEN** DELETE `/api/market/deleteFood` 的body包含image字段
- **THEN** 系统删除对应的磁盘图片文件，再执行菜品移除

### Requirement: 更新菜品图片时清理旧图片
系统SHALL在更新菜品图片时，先删除旧图片文件再替换为新图片。

#### Scenario: 替换菜品图片
- **WHEN** PUT `/api/market/updateFood` body包含oldImage
- **THEN** 系统先删除oldImage对应的磁盘文件，再更新菜品为新图片