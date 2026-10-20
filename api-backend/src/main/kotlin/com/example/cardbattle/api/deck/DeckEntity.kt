package com.example.cardbattle.api.deck

import com.example.cardbattle.api.user.UserEntity
import jakarta.persistence.*

@Entity
@Table(name = "\"Deck\"", schema = "public")
class DeckEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    @Column(name = "name", nullable = false, length = 150)
    var name: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userid", nullable = false)
    var user: UserEntity,
)
