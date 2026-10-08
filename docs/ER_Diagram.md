```
mermaid
erDiagram
    USER }o--o{ CARD : owns
    CARD }o--o{ ATTRIBUTE : has
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
    ATTRIBUTE {
        string id
        string name
    }
```