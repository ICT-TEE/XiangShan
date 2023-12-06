## 本文档对ROT的接口函数进行了简单介绍，目前包括了
- [HMAC](./hmac_api.md) （包含HMAC和SHA256模式，使用时需要依次调用hmac_sha256_init()、hmac_sha256_update()、hmac_sha256_final()）
- [KMAC](./kmac_api.md) (包含了KMAC的一般用法，如SHAKE256算法)
- [KMAC_CRYPTO](./kmac_crypto_api.md) (包含了kmac sha3_224/256/384/512, shake_128/256, cSHAKE_128/256, kmac_128/256d)
- [ENTROPY](./entropy_api.md) (包含对随机数发生器的调用，如实例初始化、随机数获取等)
- [KeyManger](./keymgr_api.md) (包含对密钥管理模块的使用，如密钥初始化、密钥状态修改、密钥管理器状态查询等)