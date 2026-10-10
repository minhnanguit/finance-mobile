package com.uit.finance.core.designsystem.icon

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.ArrowDownLeft
import com.composables.icons.lucide.ArrowDownUp
import com.composables.icons.lucide.ArrowLeftRight
import com.composables.icons.lucide.ArrowUpRight
import com.composables.icons.lucide.BadgePercent
import com.composables.icons.lucide.Banknote
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Bus
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.Car
import com.composables.icons.lucide.ChartColumn
import com.composables.icons.lucide.ChartPie
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleDollarSign
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.Coins
import com.composables.icons.lucide.Dumbbell
import com.composables.icons.lucide.Ellipsis
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Film
import com.composables.icons.lucide.Fuel
import com.composables.icons.lucide.Gamepad2
import com.composables.icons.lucide.Gift
import com.composables.icons.lucide.GraduationCap
import com.composables.icons.lucide.HandCoins
import com.composables.icons.lucide.Heart
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Inbox
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Phone
import com.composables.icons.lucide.PiggyBank
import com.composables.icons.lucide.Plane
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Shield
import com.composables.icons.lucide.Shirt
import com.composables.icons.lucide.ShoppingBag
import com.composables.icons.lucide.ShoppingCart
import com.composables.icons.lucide.SlidersHorizontal
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Stethoscope
import com.composables.icons.lucide.Tag
import com.composables.icons.lucide.Target
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.TrendingUp
import com.composables.icons.lucide.UserRound
import com.composables.icons.lucide.Utensils
import com.composables.icons.lucide.Wallet
import com.composables.icons.lucide.Wifi
import com.composables.icons.lucide.X
import com.composables.icons.lucide.Zap

/**
 * Mọi icon của app đi qua đây; bộ icon thật (Lucide, MIT) chỉ được import trong file này nên đổi bộ icon
 * = sửa một file. Dùng qua `Icon(FinanceIcons.Wallet, contentDescription = …)`: tự nhận tint từ
 * `LocalContentColor`, kích thước chuẩn 24dp. Icon theo danh mục: [categoryIcon].
 */
object FinanceIcons {
    // ---- Điều hướng ----
    val Home: ImageVector get() = Lucide.House
    val Transactions: ImageVector get() = Lucide.ArrowLeftRight
    val Budget: ImageVector get() = Lucide.ChartPie
    val Profile: ImageVector get() = Lucide.UserRound
    val Reports: ImageVector get() = Lucide.ChartColumn
    val ChevronRight: ImageVector get() = Lucide.ChevronRight
    val ChevronLeft: ImageVector get() = Lucide.ChevronLeft

    // ---- Hành động ----
    val Add: ImageVector get() = Lucide.Plus
    val Settings: ImageVector get() = Lucide.SlidersHorizontal
    val Bell: ImageVector get() = Lucide.Bell
    val More: ImageVector get() = Lucide.Ellipsis
    val MoreVertical: ImageVector get() = Lucide.EllipsisVertical
    val Search: ImageVector get() = Lucide.Search
    val Edit: ImageVector get() = Lucide.Pencil
    val Delete: ImageVector get() = Lucide.Trash2
    val Check: ImageVector get() = Lucide.Check
    val Close: ImageVector get() = Lucide.X
    val Show: ImageVector get() = Lucide.Eye
    val Hide: ImageVector get() = Lucide.EyeOff
    val Refresh: ImageVector get() = Lucide.RefreshCw
    val Offline: ImageVector get() = Lucide.CloudOff
    val Lock: ImageVector get() = Lucide.Lock
    val Shield: ImageVector get() = Lucide.Shield
    val LogOut: ImageVector get() = Lucide.LogOut
    val Calendar: ImageVector get() = Lucide.CalendarDays
    val Clock: ImageVector get() = Lucide.Clock
    val Tag: ImageVector get() = Lucide.Tag
    val Inbox: ImageVector get() = Lucide.Inbox

    // ---- Tiền & ví ----
    val Wallet: ImageVector get() = Lucide.Wallet
    val Cash: ImageVector get() = Lucide.Banknote
    val Bank: ImageVector get() = Lucide.Landmark
    val EWallet: ImageVector get() = Lucide.Smartphone
    val Income: ImageVector get() = Lucide.ArrowDownLeft
    val Expense: ImageVector get() = Lucide.ArrowUpRight
    val Transfer: ImageVector get() = Lucide.ArrowDownUp
    val Receipt: ImageVector get() = Lucide.Receipt
    val Coins: ImageVector get() = Lucide.Coins
    val Savings: ImageVector get() = Lucide.PiggyBank

    // ---- Danh mục mặc định ----
    val Food: ImageVector get() = Lucide.Utensils
    val Shopping: ImageVector get() = Lucide.ShoppingBag
    val Groceries: ImageVector get() = Lucide.ShoppingCart
    val Transport: ImageVector get() = Lucide.Car
    val PublicTransport: ImageVector get() = Lucide.Bus
    val Fuel: ImageVector get() = Lucide.Fuel
    val Gift: ImageVector get() = Lucide.Gift
    val Health: ImageVector get() = Lucide.Stethoscope
    val Entertainment: ImageVector get() = Lucide.Film
    val Games: ImageVector get() = Lucide.Gamepad2
    val Education: ImageVector get() = Lucide.GraduationCap
    val Utilities: ImageVector get() = Lucide.Zap
    val Internet: ImageVector get() = Lucide.Wifi
    val Phone: ImageVector get() = Lucide.Phone
    val Clothing: ImageVector get() = Lucide.Shirt
    val Fitness: ImageVector get() = Lucide.Dumbbell
    val Travel: ImageVector get() = Lucide.Plane
    val Salary: ImageVector get() = Lucide.HandCoins
    val Investment: ImageVector get() = Lucide.TrendingUp
    val Fee: ImageVector get() = Lucide.BadgePercent
    val Bonus: ImageVector get() = Lucide.Sparkles
    val Goal: ImageVector get() = Lucide.Target
    val Charity: ImageVector get() = Lucide.Heart
    val OtherMoney: ImageVector get() = Lucide.CircleDollarSign
}
