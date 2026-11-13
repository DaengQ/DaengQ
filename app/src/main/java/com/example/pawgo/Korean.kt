package com.example.pawgo

/** 마지막 글자에 받침이 있는지. 한글이 아니면 받침이 없다고 본다. */
fun String.hasBatchim(): Boolean {
    val last = lastOrNull() ?: return false
    if (last !in '가'..'힣') return false
    return (last - '가') % 28 != 0
}

/** 강아지 이름에 맞는 조사. 예: 콩이는, 뽀삐는, 보름은 */
fun String.withEunNeun(): String = this + if (hasBatchim()) "은" else "는"

/** 예: 콩이가, 보름이 */
fun String.withIGa(): String = this + if (hasBatchim()) "이" else "가"

/** 받침 있는 이름을 부를 때 붙이는 '이'. 예: 봄 → 봄이, 콩이 → 콩이 */
fun String.withCallingI(): String = this + if (hasBatchim()) "이" else ""
