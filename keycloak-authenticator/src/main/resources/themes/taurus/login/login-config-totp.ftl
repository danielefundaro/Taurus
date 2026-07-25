<#import "template.ftl" as layout>
<@layout.registrationLayout
    displayMessage=!messagesPerField.existsError('totp','userLabel');
    section>

    <#if section = "header">
        <h1 class="taurus-title">${msg("loginTotpTitle")}</h1>
        <p class="taurus-subtitle">&nbsp;</p>

    <#elseif section = "form">

        <ol class="taurus-totp-steps">

            <!-- Step 1: install app -->
            <li>
                <p class="taurus-totp-step-text">${msg("loginTotpStep1")}</p>
                <ul class="taurus-totp-apps">
                    <#list totp.supportedApplications as app>
                        <li>${app}</li>
                    </#list>
                </ul>
            </li>

            <!-- Step 2: QR code -->
            <li>
                <p class="taurus-totp-step-text">${msg("loginTotpStep2")}</p>
                <div class="taurus-qr-wrapper">
                    <img src="data:image/png;base64,${totp.totpSecretQrCode}"
                         alt="QR Code"
                         class="taurus-qr-img" />
                </div>
                <p class="taurus-totp-manual-label">${msg("loginTotpUnableToScan")}</p>
                <a href="${totp.qrUrl}" class="taurus-totp-link" id="mode-manual">
                    ${msg("loginTotpUnableToScan")}
                </a>
                <div id="totp-manual-container" style="display: none;" class="taurus-totp-secret-box">
                    <p class="taurus-totp-manual-label">${msg("loginTotpManualStep2")}</p>
                    <code class="taurus-totp-secret">${totp.totpSecretEncoded}</code>
                    <p class="taurus-totp-manual-label" style="margin-top:10px">${msg("loginTotpManualStep3")}</p>
                    <ul class="taurus-totp-info-list">
                        <li>${msg("loginTotpType")}: ${msg("loginTotp." + totp.policy.type)}</li>
                        <li>${msg("loginTotpAlgorithm")}: ${totp.policy.algorithm}</li>
                        <li>${msg("loginTotpDigits")}: ${totp.policy.digits}</li>
                        <#if totp.policy.type = "totp">
                            <li>${msg("loginTotpInterval")}: ${totp.policy.period}</li>
                        <#else>
                            <li>${msg("loginTotpCounter")}: ${totp.policy.initialCounter}</li>
                        </#if>
                    </ul>
                </div>
            </li>

            <!-- Step 3: enter code -->
            <li>
                <p class="taurus-totp-step-text">${msg("loginTotpStep3")}</p>
                <p class="taurus-totp-step-text">${msg("loginTotpStep3DeviceName")}</p>

                <form action="${url.loginAction}" method="post">
                    <div class="taurus-field" style="margin-top: 16px;">
                        <label for="totp" class="taurus-label">
                            ${msg("authenticatorCode")} <span class="taurus-required">*</span>
                        </label>
                        <input
                            id="totp"
                            name="totp"
                            type="text"
                            inputmode="numeric"
                            autocomplete="one-time-code"
                            class="taurus-input<#if messagesPerField.existsError('totp')> taurus-input-error</#if>"
                            placeholder="000000"
                        />
                        <#if messagesPerField.existsError('totp')>
                            <span class="taurus-field-error">
                                ${kcSanitize(messagesPerField.get('totp'))?no_esc}
                            </span>
                        </#if>
                    </div>

                    <div class="taurus-field">
                        <label for="userLabel" class="taurus-label">
                            ${msg("loginTotpDeviceName")}
                            <#if totp.otpCredentials?size gte 1><span class="taurus-required">*</span></#if>
                        </label>
                        <input
                            id="userLabel"
                            name="userLabel"
                            type="text"
                            class="taurus-input<#if messagesPerField.existsError('userLabel')> taurus-input-error</#if>"
                            placeholder="${msg("loginTotpDeviceName")}"
                        />
                        <#if messagesPerField.existsError('userLabel')>
                            <span class="taurus-field-error">
                                ${kcSanitize(messagesPerField.get('userLabel'))?no_esc}
                            </span>
                        </#if>
                    </div>

                    <#if isAppInitiatedAction??>
                    <label class="taurus-checkbox-label taurus-logout-check">
                        <input type="checkbox" id="logout-sessions" name="logoutSessions" value="on" checked>
                        <span>${msg("logoutOtherSessions")}</span>
                    </label>
                    </#if>

                    <input type="hidden" name="totpSecret" value="${totp.totpSecret}" />

                    <div class="taurus-totp-buttons">
                        <button class="taurus-btn-primary" type="submit" name="submitAction" value="Save">
                            ${msg("doSubmit")}
                        </button>
                        <#if isAppInitiatedAction??>
                        <button class="taurus-btn-secondary" type="submit" name="cancel-aia" value="true">
                            ${msg("doCancel")}
                        </button>
                        </#if>
                    </div>
                </form>
            </li>

        </ol>

        <script>
            (function() {
                var link = document.getElementById('mode-manual');
                var box  = document.getElementById('totp-manual-container');
                if (!link || !box) return;
                link.addEventListener('click', function(e) {
                    e.preventDefault();
                    box.style.display = box.style.display === 'none' ? 'block' : 'none';
                });
            })();
        </script>
    </#if>

</@layout.registrationLayout>
