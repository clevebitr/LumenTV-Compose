package com.corner.bean.enums


enum class PlayerType(
    val display: String,
    val id: String
) {
    Innie("内部", "innie"),
    Outie("外部", "outie"),
    Web("浏览器", "web");

    companion object {
        fun getById(id: String):PlayerType{
            return when(id.lowercase()){
                Innie.id -> Innie
                Outie.id -> Outie
                Web.id -> Web
                else -> Outie
            }
        }
    }
}