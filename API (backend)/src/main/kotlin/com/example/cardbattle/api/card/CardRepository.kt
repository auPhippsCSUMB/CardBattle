package com.example.cardbattle.api.card

import org.springframework.data.jpa.repository.JpaRepository

interface CardRepository : JpaRepository<CardEntity, Int>