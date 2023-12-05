
## 函数：kmac_key_length_check

**功能描述：**

检查给定的密钥长度是否适用于KMAC。

**参数：**

- key_len：输入的密钥长度。

**返回值：**

- 如果有效，返回OTCRYPTO_OK，否则返回错误。

**函数原型：**

```c
status_t kmac_key_length_check(size_t key_len);
```

## 函数：kmac_hwip_default_configure

**功能描述：**

设置HWIP的"全局"配置。

**主要步骤：**

本函数将一些需要在会话级别配置的选项设为默认值。目前，这只是临时解决方案。未来计划定义配置结构体，并将其作为参数传递。

**警告：**

- 本函数设置了`entropy_ready`，这会触发kmac_entropy的FSM跳转到下一步。因此，调用此函数的程序应确保事先正确配置了熵。

**返回值：**

- 错误代码。

**函数原型：**

```c
status_t kmac_hwip_default_configure(void);
```

## 函数：kmac_sha3_224

**功能描述：**

一次性计算SHA-3-224。

**警告：**

- 调用者必须确保`digest`缓冲区足够大，至少能存储结果摘要（至少224 / 8 = 28字节）。

**参数：**

- message：输入消息。
- digest：返回结果的摘要缓冲区。

**返回值：**

- 错误状态。

**函数原型：**

```c
status_t kmac_sha3_224(crypto_const_uint8_buf_t message, crypto_uint8_buf_t *digest);
```

## 函数：kmac_sha3_256

**功能描述：**

一次性计算SHA-3-256。

**警告：**

- 调用者必须确保`digest`缓冲区足够大，至少能存储结果摘要（至少256 / 8 = 32字节）。

**参数：**

- message：输入消息。
- digest：返回结果的摘要缓冲区。

**返回值：**

- 错误状态。

**函数原型：**

```c
status_t kmac_sha3_256(crypto_const_uint8_buf_t message, crypto_uint8_buf_t *digest);
```

## 函数：kmac_sha3_384

**功能描述：**

一次性计算SHA-3-384。

**警告：**

- 调用者必须确保`digest`缓冲区足够大，至少能存储结果摘要（至少384 / 8 = 48字节）。

**参数：**

- message：输入消息。
- digest：返回结果的摘要缓冲区。

**返回值：**

- 错误状态。

**函数原型：**

```c
status_t kmac_sha3_384(crypto_const_uint8_buf_t message, crypto_uint8_buf_t *digest);
```

## 函数：kmac_sha3_512

**功能描述：**

一次性计算SHA-3-512。

**警告：**

- 调用者必须确保`digest`缓冲区足够大，至少能存储结果摘要（至少512 / 8 = 64字节）。

**参数：**

- message：输入消息。
- digest：返回结果的摘要缓冲区。

**返回值：**

- 错误状态。

**函数原型：**

```c
status_t kmac_sha3_512(crypto_const_uint8_buf_t message, crypto_uint8_buf_t *digest);
```

## 函数：kmac_shake_128

**功能描述：**

一次性计算SHAKE-128。

**警告：**

- 调用者必须确保`digest->len`包含了请求的摘要长度（以字节为单位）。

**参数：**

- message：输入消息。
- digest：返回结果的摘要缓冲区。

**返回值：**

- 错误状态。

**函数原型：**

```c
status_t kmac_shake_128(crypto_const_uint8_buf_tmessage, crypto_uint8_buf_t *digest);
```

## 函数：kmac_shake_256

**功能描述：**

一次性计算SHAKE-256。

**警告：**

- 调用者必须确保`digest->len`包含了请求的摘要长度（以字节为单位）。

**参数：**

- message：输入消息。
- digest：返回结果的摘要缓冲区。

**返回值：**

- 错误状态。

**函数原型：**

```c
status_t kmac_shake_256(crypto_const_uint8_buf_t message, crypto_uint8_buf_t *digest);
```


## 函数：kmac_cshake_128

**功能描述：**

一次性计算CSHAKE-128。

**警告：**

- 调用者必须确保`digest->len`包含了请求的摘要长度（以字节为单位）。
- 调用者必须确保`message`和`digest`具有正确分配的`data`字段，其长度与它们的`len`字段匹配。

**参数：**

- message：输入消息。
- func_name：函数名称。
- cust_str：自定义字符串。
- digest：返回结果的摘要缓冲区。

**返回值：**

- 错误状态。

**函数原型：**

```c
status_t kmac_cshake_128(crypto_const_uint8_buf_t message,
                         crypto_const_uint8_buf_t func_name,
                         crypto_const_uint8_buf_t cust_str,
                         crypto_uint8_buf_t *digest);
```

## 函数：kmac_cshake_256

**功能描述：**

一次性计算CSHAKE-256。

**警告：**

- 调用者必须确保`digest->len`包含了请求的摘要长度（以字节为单位）。
- 调用者必须确保`message`和`digest`具有正确分配的`data`字段，其长度与它们的`len`字段匹配。

**参数：**

- message：输入消息。
- func_name：函数名称。
- cust_str：自定义字符串。
- digest：返回结果的摘要缓冲区。

**返回值：**

- 错误状态。

**函数原型：**

```c
status_t kmac_cshake_256(crypto_const_uint8_buf_t message,
                         crypto_const_uint8_buf_t func_name,
                         crypto_const_uint8_buf_t cust_str,
                         crypto_uint8_buf_t *digest);
```

## 函数：kmac_kmac_128

**功能描述：**

一次性计算KMAC-128。

**警告：**

- 调用者必须确保`digest->len`包含了请求的摘要长度（以字节为单位）。
- 调用者必须确保`message`和`digest`具有正确分配的`data`字段，其长度与它们的`len`字段匹配。

**参数：**

- key：输入密钥，作为一个结构体。
- message：输入消息。
- cust_str：自定义字符串。
- digest：返回结果的摘要缓冲区。

**返回值：**

- 错误状态。

**函数原型：**

```c
status_t kmac_kmac_128(kmac_blinded_key_t *key,
                       crypto_const_uint8_buf_t message,
                       crypto_const_uint8_buf_t cust_str,
                       crypto_uint8_buf_t *digest);
```

## 函数：kmac_kmac_256

**功能描述：**

一次性计算KMAC-256。

**警告：**

- 调用者必须确保`digest->len`包含了请求的摘要长度（以字节为单位）。
- 调用者必须确保`message`和`digest`具有正确分配的`data`字段，其长度与它们的`len`字段匹配。

**参数：**

- key：输入密钥，作为一个结构体。
- message：输入消息。
- cust_str：自定义字符串。
- digest：返回结果的摘要缓冲区。

**返回值：**

- 错误状态。

**函数原型：**

```c
status_t kmac_kmac_256(kmac_blinded_key_t *key,
                       crypto_const_uint8_buf_t message,
                       crypto_const_uint8_buf_t cust_str,
                       crypto_uint8_buf_t *digest);
```