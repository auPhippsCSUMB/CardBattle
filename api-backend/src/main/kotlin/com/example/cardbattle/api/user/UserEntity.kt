package com.example.cardbattle.api.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "\"User\"", schema = "public")
class UserEntity (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    @Column(name = "google_subject", nullable = false, unique = true)
    var googleSubject: String,

    @Column(name = "email", nullable = false)
    var email: String,

    @Column(name = "displayname", nullable = false)
    var displayName: String,

    @Column(name = "isadmin", nullable = false)
    var isAdmin: Boolean = false
)