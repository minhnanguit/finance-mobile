package com.uit.finance.core.designsystem.icon

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Khoá icon của danh mục (`Category.icon`, do server seed theo tên Material Icons hoặc user chọn) → icon
 * của design system. Khoá lạ trả `null` để nơi gọi dùng icon theo chiều tiền. Thêm danh mục mặc định mới
 * ở backend thì thêm một dòng ở đây.
 */
fun categoryIcon(key: String?): ImageVector? = key?.trim()?.lowercase()?.let(CategoryIconCatalog::get)

private val CategoryIconCatalog: Map<String, ImageVector> by lazy {
    buildMap {
        // Danh mục mặc định của backend (DefaultCategoryTemplate) — tên Material Icons.
        put("restaurant", FinanceIcons.Food)
        put("directions_car", FinanceIcons.Transport)
        put("shopping_bag", FinanceIcons.Shopping)
        put("receipt", FinanceIcons.Receipt)
        put("home", FinanceIcons.Home)
        put("favorite", FinanceIcons.Charity)
        put("school", FinanceIcons.Education)
        put("movie", FinanceIcons.Entertainment)
        put("family", FinanceIcons.Charity)
        put("percent", FinanceIcons.Fee)
        put("more_horiz", FinanceIcons.More)
        put("payments", FinanceIcons.Salary)
        put("redeem", FinanceIcons.Bonus)
        put("trending_up", FinanceIcons.Investment)
        put("card_giftcard", FinanceIcons.Gift)

        // Bí danh theo tên gọi thông thường / Lucide, cho danh mục user tự tạo.
        put("food", FinanceIcons.Food)
        put("utensils", FinanceIcons.Food)
        put("transport", FinanceIcons.Transport)
        put("car", FinanceIcons.Transport)
        put("bus", FinanceIcons.PublicTransport)
        put("fuel", FinanceIcons.Fuel)
        put("shopping", FinanceIcons.Shopping)
        put("groceries", FinanceIcons.Groceries)
        put("shopping_cart", FinanceIcons.Groceries)
        put("bills", FinanceIcons.Receipt)
        put("housing", FinanceIcons.Home)
        put("health", FinanceIcons.Health)
        put("stethoscope", FinanceIcons.Health)
        put("education", FinanceIcons.Education)
        put("entertainment", FinanceIcons.Entertainment)
        put("games", FinanceIcons.Games)
        put("utilities", FinanceIcons.Utilities)
        put("electricity", FinanceIcons.Utilities)
        put("internet", FinanceIcons.Internet)
        put("phone", FinanceIcons.Phone)
        put("clothing", FinanceIcons.Clothing)
        put("fitness", FinanceIcons.Fitness)
        put("travel", FinanceIcons.Travel)
        put("gift", FinanceIcons.Gift)
        put("salary", FinanceIcons.Salary)
        put("bonus", FinanceIcons.Bonus)
        put("investment", FinanceIcons.Investment)
        put("fee", FinanceIcons.Fee)
        put("savings", FinanceIcons.Savings)
        put("goal", FinanceIcons.Goal)
        put("charity", FinanceIcons.Charity)
        put("other", FinanceIcons.OtherMoney)
    }
}
