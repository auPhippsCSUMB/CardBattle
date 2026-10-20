package com.example.cardbattle.api.card

import com.example.cardbattle.api.dataObjects.CardType
import com.example.cardbattle.api.user.UserEntity
import jakarta.persistence.*

@Entity
@Table(name = "\"Card\"", schema = "public")
class CardEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    @Column(name = "name", nullable = false, length = 150)
    var name: String,

    @Column(name = "imgurl", length = 500)
    var imgURL: String? = null,

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    var type: CardType,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author", nullable = false)
    var author: UserEntity,
)