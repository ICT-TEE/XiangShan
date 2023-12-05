## kmac_shake256_configure()
**功能**：启动时配置KMAC模块。

此函数将 KMAC 模块设置为使用软件熵（因为我们的 SPHINCS+ 没有秘密输入）并将模式设置为 SHAKE-256。

**返回**：表示操作是否成功的错误代码。

## kmac_shake256_start()
**功能**：开始 SHAKE-256 哈希操作。

必须在 `kmac_shake256_configure()` 之后调用。此函数会阻塞，直到 KMAC 硬件处于空闲状态。

此驱动程序支持以下模式的 SHAKE-256 哈希：
- `kmac_shake256_start` 调用一次
- `kmac_shake256_absorb` 调用零次或多次
- `kmac_shake256_squeeze_start` 调用一次
- `kmac_shake256_squeeze_end` 调用一次

无需在 SHAKE-256 规范中将 `1111` 填充添加到输入；这将在 squeeze_start() 中自动发生。

**返回**：表示操作是否成功的错误代码。

## kmac_shake256_absorb()
**功能**：吸收 SHAKE-256 哈希操作的更多输入。

调用者必须首先调用 `kmac_shake256_configure()` 和 `kmac_shake256_start()`。实现依赖于 KMAC 配置的具体细节（特别是熵设置）。

阻塞直到所有输入都被写入。

为了获得最佳性能，`in` 应该是 32b 对齐的，尽管此函数确实可以处理未对齐的缓冲区。

**参数**：
- `in`：输入缓冲区
- `inlen`：输入长度（字节）

**返回**：表示操作是否成功的错误代码。

## kmac_shake256_absorb_words()
**功能**：吸收 SHAKE-256 哈希操作的更多输入。

与 `kmac_shake256_absorb` 相同，但接受的输入是32位字而不是字节，因此速度更快，因为不需要进行对齐检查。

调用者必须首先调用 `kmac_shake256_configure()` 和 `kmac_shake256_start()`。实现依赖于 KMAC 配置的具体细节（特别是熵设置）。

阻塞直到所有输入都被写入。

**参数**：
- `in`：输入缓冲区
- `inlen`：输入长度（字）

**返回**：表示操作是否成功的错误代码。

## kmac_shake256_squeeze_start()
**功能**：开始 SHAKE-256 哈希操作的挤压阶段。

此函数将从 `absorb` 状态转移到 `squeeze` 状态，并将 SHAKE-256 后缀添加到消息中。它还将初始化内部上下文对象。

调用者必须首先调用 `kmac_shake256_configure()` 和 `kmac_shake256_start()`。不需要在调用此函数前调用 `kmac_shake256_absorb()`；在这种情况下，结果摘要将简单地是空消息的摘要。

**返回**：表示操作是否成功的错误代码。

## kmac_shake256_squeeze_end()
**功能**：从 SHAKE-256 哈希操作中挤压输出，并结束操作。

调用者必须首先调用 `kmac_shake256_configure` 和 `kmac_shake256_squeeze_start()`。

阻塞直到所有输出都被写入。结束操作后，此函数不会阻塞，直到 KMAC 处于空闲状态；错误可能会在下一次调用 `kmac_shake256_start` 中出现。

**参数**：
- `out`：输出缓冲区
- `outlen`：期望的输出长度（字）

**返回**：表示操作是否成功的错误代码。