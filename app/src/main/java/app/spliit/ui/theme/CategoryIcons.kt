package app.spliit.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildFriendly
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Light
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WineBar
import androidx.compose.ui.graphics.vector.ImageVector
import app.spliit.data.model.ExpenseCategory

/**
 * The glyph that stands for an expense category.
 *
 * The web app maps every category to a Lucide glyph, and the reference maps those to SF Symbols,
 * keyed the same way — `"<grouping>/<name>"`. This is that map with the Material icons chosen in
 * the audit, so the three products agree glyph for glyph on what a taxi or a bag of groceries
 * looks like. A category the server adds later falls through to the banknote.
 */
object CategoryIcons {

    /** What the web app falls back to, and so do we. */
    private val Fallback = Icons.Filled.Payments

    private val icons: Map<String, ImageVector> = mapOf(
        "Uncategorized/General" to Icons.Filled.Payments,
        "Uncategorized/Payment" to Icons.Filled.Payments,

        "Entertainment/Entertainment" to Icons.Filled.ConfirmationNumber,
        "Entertainment/Games" to Icons.Filled.Casino,
        "Entertainment/Movies" to Icons.Filled.Movie,
        "Entertainment/Music" to Icons.Filled.MusicNote,
        "Entertainment/Sports" to Icons.Filled.FitnessCenter,

        "Food and Drink/Food and Drink" to Icons.Filled.Restaurant,
        "Food and Drink/Dining Out" to Icons.Filled.MenuBook,
        "Food and Drink/Groceries" to Icons.Filled.ShoppingCart,
        "Food and Drink/Liquor" to Icons.Filled.WineBar,

        "Home/Home" to Icons.Filled.Home,
        "Home/Electronics" to Icons.Filled.Power,
        "Home/Furniture" to Icons.Filled.Chair,
        "Home/Household Supplies" to Icons.Filled.Light,
        "Home/Maintenance" to Icons.Filled.Build,
        "Home/Mortgage" to Icons.Filled.AccountBalance,
        "Home/Pets" to Icons.Filled.Pets,
        "Home/Rent" to Icons.Filled.Key,
        "Home/Services" to Icons.Filled.Handyman,

        "Life/Childcare" to Icons.Filled.ChildFriendly,
        "Life/Clothing" to Icons.Filled.Checkroom,
        "Life/Donation" to Icons.Filled.PanTool,
        "Life/Education" to Icons.Filled.LibraryBooks,
        "Life/Gifts" to Icons.Filled.CardGiftcard,
        "Life/Insurance" to Icons.Filled.AccountBalance,
        "Life/Medical Expenses" to Icons.Filled.MedicalServices,
        "Life/Taxes" to Icons.Filled.Payments,

        "Transportation/Transportation" to Icons.Filled.DirectionsBus,
        "Transportation/Bicycle" to Icons.Filled.DirectionsBike,
        "Transportation/Bus/Train" to Icons.Filled.Tram,
        "Transportation/Car" to Icons.Filled.DirectionsCar,
        "Transportation/Gas/Fuel" to Icons.Filled.LocalGasStation,
        "Transportation/Hotel" to Icons.Filled.Hotel,
        "Transportation/Parking" to Icons.Filled.LocalParking,
        "Transportation/Plane" to Icons.Filled.Flight,
        "Transportation/Taxi" to Icons.Filled.LocalTaxi,

        "Utilities/Utilities" to Icons.Filled.Payments,
        "Utilities/Cleaning" to Icons.Filled.AutoAwesome,
        "Utilities/Electricity" to Icons.Filled.Bolt,
        "Utilities/Heat/Gas" to Icons.Filled.Thermostat,
        "Utilities/Trash" to Icons.Filled.Delete,
        "Utilities/TV/Phone/Internet" to Icons.Filled.Phone,
        "Utilities/Water" to Icons.Filled.WaterDrop,
    )

    /**
     * The glyph for a category, or the banknote when the server sends none.
     *
     * Several category *names* contain a slash of their own ("Bus/Train", "Gas/Fuel"), which is
     * why this is a flat string key rather than a pair.
     */
    fun icon(category: ExpenseCategory?): ImageVector {
        if (category == null) return Fallback
        return icons["${category.grouping}/${category.name}"] ?: Fallback
    }
}
