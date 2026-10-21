package com.fundaro.zodiac.keycloakauthenticator.otp;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.keycloak.models.KeycloakSession;
import org.keycloak.services.managers.AppAuthManager;

import java.util.Map;

/**
 * Codes of the authentication app for the electronic signature of Taurus, under {@code /realms/{realm}/taurus-otp}. The
 * checks are in {@link TaurusOtp}.
 */
public class TaurusOtpResource {

    private final TaurusOtp otp;

    public TaurusOtpResource(KeycloakSession session) {
        this.otp = new TaurusOtp(session, current -> new AppAuthManager.BearerTokenAuthenticator(current).authenticate());
    }

    /**
     * {@code { "configured": true|false }} for the user.
     */
    @GET
    @Path("users/{userId}/status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response status(@PathParam("userId") String userId) {
        TaurusOtp.Outcome outcome = otp.status(userId);
        if (outcome.configured() == null) return error(outcome.status());
        return Response.ok(Map.of("configured", outcome.configured()), MediaType.APPLICATION_JSON_TYPE).build();
    }

    /**
     * {@code 204} when the code is valid, {@code 422} otherwise.
     */
    @POST
    @Path("users/{userId}/verify")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response verify(@PathParam("userId") String userId, VerifyRequest request) {
        TaurusOtp.Outcome outcome = otp.verify(userId, request == null ? null : request.getCode());
        return outcome.status() == Response.Status.NO_CONTENT.getStatusCode() ? Response.noContent().build() : error(outcome.status());
    }

    /**
     * Keycloak requires a media type on error responses.
     */
    private static Response error(int status) {
        String error = switch (status) {
            case 403 -> "forbidden";
            case 404 -> "user_not_found";
            default -> "invalid_code";
        };
        return Response.status(status).entity(Map.of("error", error)).type(MediaType.APPLICATION_JSON_TYPE).build();
    }

    /**
     * Body of a verification.
     */
    public static class VerifyRequest {

        private String code;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        @Override
        public String toString() {
            return "VerifyRequest[code=***]";
        }
    }
}
