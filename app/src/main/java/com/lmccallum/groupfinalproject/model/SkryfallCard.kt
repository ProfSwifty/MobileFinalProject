package com.lmccallum.groupfinalproject.model

data class ScryfallCard(
    val id: String,
    val name: String,
    val mana_cost: String?,
    val type_line: String,
    val oracle_text: String?,
    val flavor_text: String?,
    val power: String?,
    val toughness: String?,
    val image_uris: Map<String, String>?,
    val set_name: String,
    val artist: String?,
    val rarity: String?
)