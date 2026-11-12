plugins {
    alias(libs.plugins.finance.kmp.compose)
}

/*
 * UI dùng chung biết model ledger (giống `core:ui` của Now in Android): nhãn + icon theo loại ví/giao dịch,
 * dòng giao dịch, câu báo lỗi cho mã `ledger.*`. `core/designsystem` thì không biết gì về ledger.
 */
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.designsystem)
            api(projects.core.presentation)
            api(projects.core.ledger) // model + mã lỗi; chỉ dùng package domain của ledger
            api(projects.core.budget) // mã lỗi budget.*; chỉ dùng package domain
            api(projects.core.sync) // SyncOverview cho badge đồng bộ
            implementation(libs.koin.core)
        }
    }
}
