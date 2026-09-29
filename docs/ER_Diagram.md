erDiagram
    USER }o--o{ CARD : owns
    CARD }o--|{ TYPE : is
    TYPE ||--|| TYPE : matchup
    USER {
        string id
        string name
        string email
    }
    CARD {
        string id
        string image_url
        string name
    }
    TYPE {
        string id
        string name
    }