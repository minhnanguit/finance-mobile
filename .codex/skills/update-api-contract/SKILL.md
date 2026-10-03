---
name: update-api-contract
description: Cập nhật bản pin hợp đồng OpenAPI trong finance-mobile (api/openapi.yaml + api/VERSION + regenerate client + sửa adapter). Dùng khi backend release contract mới, khi cần endpoint/field mới, hoặc khi build lỗi vì DTO generated đổi.
---

# Re-pin hợp đồng API

Nguồn chuẩn trong repo: `api/README.md`. Skill này là quy trình thao tác; khi mâu thuẫn, `api/README.md` thắng.

Bối cảnh: `api/openapi.yaml` là **copy nguyên văn** contract của backend, `api/VERSION` giữ semver. Việc nâng pin là **một PR riêng, có chủ ý** — không gộp chung với feature.

## 1. Đọc thay đổi phía backend

- Contract nguồn: `finance-backend/api/openapi.yaml` (cùng workspace) hoặc asset của GitHub Release tag `vX.Y.Z`.
- So sánh trước khi copy:

```bash
diff -u api/openapi.yaml ../finance-backend/api/openapi.yaml | head -100
cat api/VERSION
```

- Major tăng ⇒ breaking, path đổi sang `/api/v2`; `/v1` còn sống ít nhất 2 release. Báo user phạm vi ảnh hưởng **trước khi** đổi.

## 2. Copy + bump trong một commit

```bash
cp ../finance-backend/api/openapi.yaml api/openapi.yaml
echo "1.1.0" > api/VERSION          # đúng bằng info.version của spec
```

**Không sửa tay nội dung spec.** Nếu thấy spec sai, sửa ở repo backend rồi copy lại.

## 3. Regenerate

```bash
make api
```

Output: `core/network/build/generated/openapi/src/commonMain/kotlin/.../generated/` (tất cả `internal`).

## 4. Sửa lớp bọc

Chỉ hai chỗ được đụng tới client generated:

| File | Việc |
|---|---|
| `core/network/.../api/internal/GeneratedApiAdapters.kt` | Nơi **duy nhất** import `…core.network.generated.*` — cập nhật signature |
| `core/network/.../api/UserApi.kt`, `api/model/*Dtos.kt` | Interface + DTO mà feature nhìn thấy |

Sau đó sửa mapper trong `feature/*/data/mapper/`. Nếu field mới cần hiển thị → cập nhật domain model + State.

Lưu ý riêng: refresh token KHÔNG thuộc contract backend nữa — `core/auth` gọi thẳng token endpoint của Keycloak. Đổi contract không ảnh hưởng luồng refresh.

⚠️ Generator `kotlin`/`multiplatform` 7.14 sinh code sai cú pháp cho security scheme `openIdConnect` (`mapOf(, ...`). Spec phải chỉ dùng `http bearer`; thấy lỗi này thì sửa spec ở backend.

## 5. Verify

```bash
make test
make arch
make apk
```

Konsist sẽ bắt ngay nếu có file ngoài `core/network` lỡ import package generated.

Chạy thử với backend thật:

```bash
cd ../finance-backend && make up run
```

Android emulator gọi host qua `http://10.0.2.2:8080` ở build debug (`defaultApiBaseUrl()`).

## 6. PR

Tiêu đề theo quy ước repo: `api: pin contract <old> -> <new>`.

## Cạm bẫy đã ghi trong `core/network/build.gradle.kts`

- **Không** set `serializationLibrary` (library `multiplatform` đã ngụ ý kotlinx-serialization; set thêm → `@Serializable` bị sinh hai lần).
- `typeMappings` map `date-time` → `kotlin.time.Instant`; bỏ đi thì field thời gian tụt về `String`.
- `nonPublicApi=true` giữ client `internal` — đừng tắt để "cho tiện".

## Checklist

- [ ] `api/openapi.yaml` là copy nguyên văn, không sửa tay
- [ ] `api/VERSION` khớp `info.version` trong spec
- [ ] Chỉ `GeneratedApiAdapters.kt` import package generated
- [ ] 3 lệnh verify xanh
- [ ] PR riêng, đặt tên `api: pin contract <old> -> <new>`
