package fr.vriege.anilib.framework.http;

/**
 * HTTP request methods supported by {@link HttpRequest} and
 * {@link HttpTransport}.
 */
public enum HttpMethod {
    /** Retrieves a representation without a request body. */
    GET,

    /** Retrieves response metadata without a request body. */
    HEAD,

    /** Submits a representation or command to a resource. */
    POST,

    /** Replaces or creates a resource representation. */
    PUT,

    /** Applies a partial modification to a resource. */
    PATCH,

    /** Requests removal of a resource. */
    DELETE
}
