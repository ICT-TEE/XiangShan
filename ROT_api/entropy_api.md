## entropy_complex_init()
**功能**：在连续模式下配置熵复合体。

熵复合体在连续模式下配置，并启用 FIPS 模式。这是 entropy_src、csrng、edn0 和 edn1 块的默认操作模式。

**返回**：`status_t` 格式的操作状态。

## entropy_csrng_instantiate()
**功能**：用新的种子值实例化软件 CSRNG。

软件 CSRNG 指的是可供软件使用的 CSRNG 硬件实例。

**参数**：
- `disable_trng_input`：设置为 kHardendedTrue 以禁用硬件提供的随机种子数据。这启用了 CSRNG 的确定性模式。
- `seed_material`：用于给 CSRNG 播种的数据。当 `disable_trng_input` 设置为 `kHardenedFalse` 时，由硬件提供的熵进行 XOR，否则直接用作种子。
- **返回**：`status_t` 格式的操作状态。

## entropy_csrng_reseed()
**功能**：为软件 CSRNG 重播种。

**参数**：
- `disable_trng_input`：设置为 kHardendedTrue 以禁用硬件提供的熵。
- `seed_material`：用于重播种 CSRNG 的数据。当 `disable_trng_input` 设置为 `kHardenedFalse` 时，与硬件提供的熵进行 XOR，否则直接用作种子。
- **返回**：`status_t` 格式的操作状态。

## entropy_csrng_update()
**功能**：更新软件 CSRNG 的状态。

此命令不会更新 CSRNG 的内部重播种计数器。

**参数**：
- `seed_material`：用于 CSRNG 更新操作的额外数据。没有从硬件加载额外的熵。
- **返回**：`status_t` 格式的操作状态。

## entropy_csrng_generate_start()
**功能**：从软件 CSRNG 请求数据。

使用 `entropy_csrng_generate_data_get()` 从 CSRNG 输出缓冲区中读取数据。

请参阅 `entropy_csrng_generate()`，用于在单个调用中请求和读取 CSRNG 输出。

**参数**：
- `seed_material`：用于 CSRNG 生成操作的额外数据。没有从硬件加载额外的熵。
- `len`：要生成的 uint32_t 字的数量。
- **返回**：`status_t` 格式的操作状态。

## entropy_csrng_generate_data_get()
**功能**：读取软件 CSRNG 的输出。

需要事先调用 `entropy_csrng_generate_start()` 函数，否则该函数将无限期阻塞。

**参数**：
- `buf`：用于填充来自 CSRNG 输出缓冲区的字的缓冲区。
- `len`：要读入 `buf` 的字的数量。
- **返回**：`status_t` 格式的操作状态。

## entropy_csrng_generate()
**功能**：请求并从软件 CSRNG 读取数据。

**参数**：
- `seed_material`：用于 CSRNG 生成操作的额外数据。没有从硬件加载额外的熵。
- `buf`：用于填充来自 CSRNG 输出缓冲区的字的缓冲区。
- `len`：要读入 `buf` 的字的数量。
- **返回**：`status_t` 格式的操作状态。如果 `len` 参数导致 128 位块级大小超过 0x800，则为 OutOfRange。

## entropy_csrng_uninstantiate()
**功能**：取消实例化软件 CSRNG。

此操作实际上重置了软件 CSRNG 实例的状态，清除了由于错误的命令语法或熵源失败可能遇到的任何错误。

**返回**：`status_t` 格式的操作状态。

## 函数：entropy_csrng_kat

**功能描述：**

在软件CSRNG实例上运行CTR DRBG已知答案测试(KATs)。

**主要步骤：**

1. 使用来自NIST的CAVP网站的测试向量进行测试。
2. 首次调用时，实例化一个密钥和向量V。
3. 再次调用时，生成一系列的密钥和向量V，并返回一串比特。

**测试向量：CTR_DRBG AES-256 无DF。**

- 熵输入（EntropyInput）：df5d73faa468649edda33b5cca79b0b05600419ccb7a879ddfec9db32ee494e5531b51de16a30f769262474c73bec010
- 随机数（Nonce）：空
- 个性化字符串（PersonalizationString）：空

**实例化命令：**

- 密钥（Key）：8c52f901632d522774c08fad0eb2c33b98a701a1861aecf3d8a25860941709fd
- V：217b52142105250243c0b2c206b8f59e

**首次调用生成命令：**

- 密钥（Key）：72f4af5c93258eb3eeec8c0cacea6c1d1978a4fad44312725f1ac43b167f2d52
- V：e86f6d07dfb551cebad80e6bf6830ac4

**第二次调用生成命令：**

- 密钥（Key）：1a1c6e5f1cccc6974436e5fd3f015bc8e9dc0f90053b73e3c19d4dfd66d1b85a
- V：53c78ac61a0bac9d7d2e92b1e73e3392
- 返回的比特串（ReturnedBits）：d1c07cd95af8a7f11012c84ce48bb8cb87189e99d40fccb1771c619bdf82ab2280b1dc2f2581f39164f7ac0c510494b3a43c41b7db17514c87b107ae793e01c5

**返回值：**

返回 `status_t` 格式的操作状态。

**函数原型：**

```c
status_t entropy_csrng_kat(void);