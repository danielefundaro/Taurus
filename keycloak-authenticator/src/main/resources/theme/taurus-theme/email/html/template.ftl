<#--
  Impaginazione comune delle e-mail di Taurus (docs/email-layout-standard.md). Il backend produce lo
  stesso HTML con templates/mail/html/layout.html: un cambio d'aspetto si fa in entrambi.

  I modelli del tema base di Keycloak chiamano emailLayout senza parametri e ricevono lo stesso aspetto.
-->
<#assign font = "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif">

<#macro emailLayout title="" preheader="" reason="">
<!DOCTYPE html>
<html lang="it">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="color-scheme" content="light">
  <meta name="supported-color-schemes" content="light">
  <title><#if title?has_content>${title} — </#if>Taurus</title>
</head>
<body style="margin:0;padding:0;background-color:#f1f5f9;font-family:${font};-webkit-font-smoothing:antialiased;">
<#if preheader?has_content>
<div style="display:none;max-height:0;overflow:hidden;opacity:0;mso-hide:all;font-size:1px;line-height:1px;color:#f1f5f9;">${preheader}</div>
</#if>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#f1f5f9;">
  <tr>
    <td align="center" style="padding:48px 16px;">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:540px;">
        <tr>
          <td style="background-color:#22c55e;height:5px;border-radius:12px 12px 0 0;font-size:0;line-height:0;">&nbsp;</td>
        </tr>
        <tr>
          <td style="background-color:#ffffff;border:1px solid #e2e8f0;border-top:none;border-radius:0 0 12px 12px;padding:40px 40px 36px;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td align="center" style="padding-bottom:32px;">
                  <#if url?? && url.resourcesUrl??>
                  <img src="${url.resourcesUrl}/img/logo.png" width="72" height="72" alt="Taurus" style="display:block;margin:0 auto;border:0;outline:none;text-decoration:none;">
                  </#if>
                  <div style="margin-top:10px;font-size:20px;font-weight:700;letter-spacing:-0.3px;color:#0f172a;font-family:${font};">Taurus</div>
                </td>
              </tr>
            </table>
            <#if title?has_content>
            <h1 style="margin:0 0 12px;font-size:19px;font-weight:600;line-height:1.4;color:#0f172a;font-family:${font};">${title}</h1>
            </#if>
            <div style="font-size:15px;line-height:1.6;color:#475569;font-family:${font};">
              <#nested>
            </div>
          </td>
        </tr>
        <tr>
          <td align="center" style="padding:24px 16px 0;">
            <p style="margin:0 0 4px;font-size:12px;line-height:1.5;color:#64748b;font-family:${font};">Taurus</p>
            <#if reason?has_content>
            <p style="margin:0;font-size:12px;line-height:1.5;color:#64748b;font-family:${font};">${reason}</p>
            </#if>
          </td>
        </tr>
      </table>
    </td>
  </tr>
</table>
</body>
</html>
</#macro>

<#macro greeting name="">
<p style="margin:0 0 8px;font-size:15px;line-height:1.6;color:#475569;font-family:${font};">Ciao<#if name?has_content> ${name}</#if>,</p>
</#macro>

<#macro paragraph>
<p style="margin:0 0 16px;font-size:15px;line-height:1.6;color:#475569;font-family:${font};"><#nested></p>
</#macro>

<#macro button href label>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
  <tr>
    <td align="center" style="padding:12px 0 28px;">
      <a href="${href}" style="display:inline-block;background-color:#15803d;color:#ffffff;text-decoration:none;font-size:15px;font-weight:600;line-height:1.2;padding:13px 32px;border-radius:8px;font-family:${font};">${label}</a>
    </td>
  </tr>
</table>
</#macro>

<#macro notes>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
  <tr>
    <td style="border-top:1px solid #e2e8f0;padding-top:20px;">
      <#nested>
    </td>
  </tr>
</table>
</#macro>

<#macro note>
<p style="margin:0 0 10px;font-size:13px;line-height:1.5;color:#64748b;font-family:${font};"><#nested></p>
</#macro>

<#macro fallbackLink href>
<p style="margin:0;font-size:12px;line-height:1.5;color:#64748b;word-break:break-all;font-family:${font};">Se il pulsante non funziona, copia questo link nel browser:<br><a href="${href}" style="color:#15803d;text-decoration:underline;">${href}</a></p>
</#macro>
