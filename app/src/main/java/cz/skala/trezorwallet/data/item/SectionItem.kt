package cz.skala.trezorwallet.data.item


data class SectionItem(
        val title: Int,
        val expandable: Boolean = false,
        val expanded: Boolean = false
) : Item()