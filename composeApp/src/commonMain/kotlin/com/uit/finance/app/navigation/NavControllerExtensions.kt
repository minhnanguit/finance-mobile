package com.uit.finance.app.navigation

import androidx.navigation.NavHostController
import com.uit.finance.feature.home.presentation.navigation.HomeDestination

/**
 * Chuyển tab kiểu bottom bar: mỗi tab giữ back stack riêng, bấm lại tab đang mở thì không làm gì.
 * Pop về `HomeDestination` (gốc của mọi phiên đã đăng nhập) chứ không về start destination của graph,
 * vì start destination có thể là `SignedOutDestination` đã bị xoá khỏi back stack lúc đăng nhập.
 */
internal fun NavHostController.navigateToTopLevel(tab: TopLevelDestination) {
    navigate(tab.route) {
        popUpTo<HomeDestination> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Mở màn con. `launchSingleTop` chặn bấm 2 lần liên tiếp đẩy 2 bản cùng màn vào back stack. */
internal fun NavHostController.navigateSingleTop(destination: Any) {
    navigate(destination) { launchSingleTop = true }
}

/** Đổi phiên (đăng nhập / đăng xuất): xoá sạch back stack để nút back không quay về phiên cũ. */
internal fun NavHostController.navigateClearingBackStack(destination: Any) {
    navigate(destination) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
