Problem:
      description: RFC 9457 Problem Details. This is the single error body returned by every endpoint.
      type: object
      required: [title, status]
      properties:
        type:
          type: string
          default: about:blank
        title:
          type: string
          example: Not Found
        status:
          type: integer
          example: 404
        detail:
          type: string
          example: Card 42 not found for this user
        instance:
          type: string
          example: /api/v1/cards/42

  responses:
    BadRequest:
      description: Validation failed or malformed JSON.
      content:
        application/problem+json:
          schema:
            $ref: '#/components/schemas/Problem'
          example:
            title: Bad Request
            status: 400
            detail: One or more fields failed validation.
    Unauthorized:
      description: Missing or invalid token.
      content:
        application/problem+json:
          schema:
            $ref: '#/components/schemas/Problem'
          example:
            title: Unauthorized
            status: 401
            detail: Authentication required.
    Forbidden:
      description: Valid token, but not allowed for this action (for example, not an admin).
      content:
        application/problem+json:
          schema:
            $ref: '#/components/schemas/Problem'
          example:
            title: Forbidden
            status: 403
            detail: You do not have permission to perform this action.
    NotFound:
      description: Resource does not exist or belongs to someone else.
      content:
        application/problem+json:
          schema:
            $ref: '#/components/schemas/Problem'
          example:
            title: Not Found
            status: 404
            detail: Card 42 not found for this user
    InternalError:
      description: Unexpected server error. The app still needs to show something useful.
      content:
        application/problem+json:
          schema:
            $ref: '#/components/schemas/Problem'
          example:
            title: Internal Server Error
            status: 500
            detail: An unexpected error occurred. Please try again later.