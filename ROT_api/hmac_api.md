## hmac_sha256_init()
**功能**：初始化 HMAC 为 SHA256 模式。

此函数重置 HMAC 模块以清除摘要寄存器。然后，它使用小端输入和摘要输出配置 HMAC 块为 SHA256 模式。

## hmac_sha256_update()
**功能**：将 `len` 字节的 `data` 发送到 SHA2-256 函数。

此函数不检查可用的 HMAC FIFO 的大小。由于此函数旨在阻塞模式下运行，因此轮询 FIFO 状态等同于在 FIFO 写入操作上停滞。

**参数**：
- `data`：从此缓冲区复制数据。
- `len`：`data` 缓冲区的大小。

## hmac_sha256_final()
**功能**：完成 SHA256 操作并写入 `digest` 缓冲区。

**参数**：
- `digest`：复制摘要到此缓冲区。


## 函数：hmac_sha256_init

**功能描述：**

初始化SHA256模式的HMAC块。

**主要步骤：**

1. 如果HMAC块处于非空闲状态，调用此函数会中止之前的操作。
2. 该函数重置HMAC模块以清除摘要寄存器。
3. 然后，它将HMAC块配置为SHA256模式，输入和输出数据都为小端模式。

**函数原型：**

```c
void hmac_sha256_init(void);
```

## 函数：hmac_hmac_init

**功能描述：**

初始化HMAC模式的HMAC块。

**主要步骤：**

1. 如果HMAC块处于非空闲状态，调用此函数会中止之前的操作。
2. 该函数重置HMAC模块以清除摘要寄存器。
3. 然后，它将HMAC块配置为SHA256模式，输入和输出数据都为小端模式。

**参数：**

- key：HMAC密钥。

**函数原型：**

```c
void hmac_hmac_init(const hmac_key_t *key);
```

## 函数：hmac_update

**功能描述：**

将`data`中的`len`字节发送到HMAC或者SHA2-256函数。

**主要步骤：**

1. 此函数不检查可用HMAC FIFO的大小。
2. 由于此函数应在阻塞模式下运行，因此轮询FIFO状态相当于在FIFO写入时停滞。

**参数：**

- data：要复制数据的缓存。
- len：`data`缓存的大小（以字节为单位）。

**函数原型：**

```c
void hmac_update(const uint8_t *data, size_t len);
```

## 函数：hmac_final

**功能描述：**

完成HMAC或SHA256操作，并写入`digest`缓存。

**主要步骤：**

1. 当设备在处理时阻塞。
2. 完成后清除硬件摘要寄存器和配置。

**参数：**

- digest：复制摘要的缓存。

**函数原型：**

```c
void hmac_final(hmac_digest_t *digest);
```