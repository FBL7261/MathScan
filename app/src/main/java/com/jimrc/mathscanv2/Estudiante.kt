package com.jimrc.mathscanv2

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class Estudiante : RealmObject {
    @PrimaryKey
    var _id: String = ""
    var nombre: String = ""
    var curso: String = ""
}
