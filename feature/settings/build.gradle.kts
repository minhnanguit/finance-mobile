plugins {
    alias(libs.plugins.finance.kmp.feature)
}

// Tab "Cá nhân": hồ sơ, đổi mật khẩu, chính sách quyền riêng tư, đăng xuất, xoá tài khoản (ADR-008).
kotlin {
    sourceSets {
        commonMain.dependencies {
            // Quy ước: chỉ data layer dùng auth/network/datastore/session; domain thuần Kotlin (Konsist chặn import
            // framework), presentation đi qua use case (review giữ, chưa có luật Konsist riêng cho việc này).
            implementation(projects.core.auth) // xác nhận lại bằng prompt=login, đổi mật khẩu (kc_action), huỷ phiên
            implementation(projects.core.network) // UserApi: GET /me, DELETE /me
            implementation(projects.core.datastore) // SessionStore + TokenProvider
            implementation(projects.core.session) // xoá sổ khi đăng xuất / xoá tài khoản
            implementation(projects.core.sync) // số thay đổi chưa gửi trước khi đăng xuất / xoá tài khoản
        }
        commonTest.dependencies {
            implementation(projects.core.ledgerTesting) // FakeSyncKit
        }
    }
}
