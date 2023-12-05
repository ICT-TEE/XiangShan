## keymgr_sw_binding_set()
**功能**：设置密钥管理器的软件绑定输入。

**参数**：
- `binding_value_sealing`：用于密封值的软件绑定。
- `binding_value_attestation`：用于认证值的软件绑定。

## keymgr_sw_binding_unlock_wait()
**功能**：阻塞，直到软件绑定寄存器解锁。

此函数可以在 `keymgr_advance_state()` 之后调用，等待软件绑定寄存器可以写入。

## keymgr_creator_max_ver_set()
**功能**：设置硅制造者的最大密钥版本。

**参数**：
- `max_key_ver`：与硅制造者的密钥管理器阶段关联的最大密钥版本。

## keymgr_owner_int_max_ver_set()
**功能**：设置硅所有者的中间最大密钥版本。

**参数**：
- `max_key_ver`：与硅所有者的中间密钥管理器阶段关联的最大密钥版本。

## keymgr_init()
**功能**：初始化密钥管理器。

初始化密钥管理器的 `entropy_reseed_interval` 并将状态提升到已初始化。

在调用此函数之前，必须将密钥管理器的工作状态设置为重置，否则会返回 `kErrorKeymgrInternal`。

**参数**：
- `entropy_reseed_interval`：在重新播种熵之前的密钥管理器周期数。
- **返回**：操作的结果。

## keymgr_advance_state()
**功能**：提升密钥管理器的状态。

在调用此函数之前，必须调用 `keymgr_state_check()` 函数，以确保密钥管理器处于预期状态并准备好接收操作命令。

调用者负责在稍后的时间调用 `keymgr_state_check()`，以确保提升过渡没有错误完成。

注意：建议在安全 mmio 的 `sec_mmio_check_values()` 函数之前调用 `keymgr_sw_binding_unlock_wait()`，以确保密钥管理器的内部状态在安全 mmio 期望表中更新。

## keymgr_state_check()
**功能**：检查密钥管理器的状态。

**参数**：
- `expected_state`：预期的密钥管理器状态。
- **返回**：如果密钥管理器处于 `expected_state` 状态，且状态为空闲或成功，则返回 `kErrorOk`；否则返回 `kErrorKeymgrInternal`。