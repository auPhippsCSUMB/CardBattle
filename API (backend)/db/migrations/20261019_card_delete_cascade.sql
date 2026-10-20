-- Manual PostgreSQL migration. This is not automatically run by Spring Boot.
-- If already applied in Railway, no further database action is necessary.
BEGIN;

ALTER TABLE public."User_owns_card"
    DROP CONSTRAINT fk_user_owns_card_card,
    ADD CONSTRAINT fk_user_owns_card_card
        FOREIGN KEY (cardid)
        REFERENCES public."Card"(id)
        ON DELETE CASCADE;

ALTER TABLE public."Card_in_deck"
    DROP CONSTRAINT fk_card_in_deck_card,
    ADD CONSTRAINT fk_card_in_deck_card
        FOREIGN KEY (cardid)
        REFERENCES public."User_owns_card"(id)
        ON DELETE CASCADE;

ALTER TABLE public."Wins"
    DROP CONSTRAINT fk_wins_winning_card,
    ADD CONSTRAINT fk_wins_winning_card
        FOREIGN KEY (winningcard)
        REFERENCES public."User_owns_card"(id)
        ON DELETE SET NULL,
    DROP CONSTRAINT fk_wins_losing_card,
    ADD CONSTRAINT fk_wins_losing_card
        FOREIGN KEY (losingcard)
        REFERENCES public."User_owns_card"(id)
        ON DELETE SET NULL;

COMMIT;
