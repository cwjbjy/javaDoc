## ADDED Requirements

### Requirement: 成功响应统一包装
系统SHALL将所有成功的控制器返回值统一包装为`{code, message, data}`格式。

#### Scenario: 包装控制器返回值
- **WHEN** 任何RestController方法成功返回数据(非void)
- **THEN** 系统自动将返回值包装为`{code: 200, message: 'success', data: <原始返回值>}`

#### Scenario: void返回不包装
- **WHEN** 控制器方法返回void(如某些DELETE操作)
- **THEN** 系统不进行包装处理

### Requirement: 异常响应统一格式
系统SHALL将所有未捕获异常和HTTP异常统一为`{statusCode, timestamp, path, message}`格式。

#### Scenario: 业务异常响应
- **WHEN** 服务层抛出特定异常(如分类名称重复、分类不存在)
- **THEN** 系统返回对应HTTP状态码，body格式为`{statusCode, timestamp, path, message}`

#### Scenario: 未知异常响应
- **WHEN** 发生未预期的运行时异常
- **THEN** 系统返回500状态码，`{statusCode: 500, timestamp, path, message: 'Internal server error'}`

### Requirement: 验证异常处理
系统SHALL对请求体DTO进行验证，验证失败时返回具体错误信息。

#### Scenario: DTO验证失败
- **WHEN** 请求体缺少必填字段
- **THEN** 系统返回400状态码，message包含具体字段错误信息