// Ports of the four JTE email templates under id/workers/src/main/resources/templates.
//
// Rendered byte-for-byte against Java-rendered golden fixtures
// (test/fixtures/emails/*.html). JTE's HTML escaping is applied exactly as
// gg.jte.html.escape.Escape does: htmlContent escapes &, < and >; htmlAttribute
// escapes ', ", & and <.

/** JTE `Escape.htmlContent` — text content context. */
export function escapeHtml(value: string): string {
  return value.replace(/[&<>]/g, (c) => (c === "&" ? "&amp;" : c === "<" ? "&lt;" : "&gt;"));
}

/** JTE `Escape.htmlAttribute` — attribute value context. */
export function escapeAttribute(value: string): string {
  return value.replace(/['"&<]/g, (c) => (c === "'" ? "&#39;" : c === '"' ? "&#34;" : c === "&" ? "&amp;" : "&lt;"));
}

export function renderMagicLinkEmail(decisionUrl: string): string {
  return `
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Login Attempt</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            background-color: #f4f4f4;
            margin: 0;
            padding: 0;
        }
        .email-container {
            max-width: 600px;
            margin: 50px auto;
            background: #ffffff;
            border: 1px solid #ddd;
            border-radius: 5px;
            box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
            overflow: hidden;
        }
        .email-header {
            background-color: #007bff;
            color: #ffffff;
            text-align: center;
            padding: 20px;
        }
        .email-body {
            padding: 30px;
            line-height: 1.6;
            color: #333333;
        }
        .email-body h1 {
            font-size: 24px;
            margin: 0 0 20px;
        }
        .email-footer {
            text-align: center;
            padding: 20px;
            font-size: 12px;
            color: #999999;
            border-top: 1px solid #ddd;
        }
        .email-footer a {
            color: #007bff;
            text-decoration: none;
        }
    </style>
</head>
<body>
<div class="email-container">
    <div class="email-header">
        <h1>Login Attempt</h1>
    </div>
    <div class="email-body">
        <p>Hi there,</p>
        <p>We received a request to login using magic link for your account. Open the link below to approve the login attempt.</p>
        <a href="${escapeAttribute(decisionUrl)}">${escapeHtml(decisionUrl)}</a>
        <p>If you didn&rsquo;t request to login, please ignore this email or contact support if you have questions.</p>
        <p>Thanks,<br>The JSS Team</p>
    </div>
    <div class="email-footer">
        <p>If you have any issues, feel free to contact our support team.</p>
        <p>&copy; 2025 Jasmine Software Solutions. All rights reserved.</p>
    </div>
</div>
</body>
</html>
`;
}

export function renderEmailChangeEmail({ verifyUrl, newEmail }: { verifyUrl: string; newEmail: string }): string {
  return `
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Verify Your Email Address</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            background-color: #f4f4f4;
            margin: 0;
            padding: 0;
        }
        .email-container {
            max-width: 600px;
            margin: 50px auto;
            background: #ffffff;
            border: 1px solid #ddd;
            border-radius: 5px;
            box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
            overflow: hidden;
        }
        .email-header {
            background-color: #28a745;
            color: #ffffff;
            text-align: center;
            padding: 20px;
        }
        .email-body {
            padding: 30px;
            line-height: 1.6;
            color: #333333;
        }
        .email-body h1 {
            font-size: 24px;
            margin: 0 0 20px;
        }
        .email-footer {
            text-align: center;
            padding: 20px;
            font-size: 12px;
            color: #999999;
            border-top: 1px solid #ddd;
        }
    </style>
</head>
<body>
<div class="email-container">
    <div class="email-header">
        <h1>Verify Your Email Address</h1>
    </div>
    <div class="email-body">
        <p>Hi there,</p>
        <p>You requested to change your email address to <strong>${escapeHtml(newEmail)}</strong>.</p>
        <p>Click the link below to verify this email address:</p>
        <p><a href="${escapeAttribute(verifyUrl)}">Verify Email Address</a></p>
        <p>This link will expire in 15 minutes.</p>
        <p>If you did not request this change, please ignore this email.</p>
        <p>Thanks,<br>The JSS Team</p>
    </div>
    <div class="email-footer">
        <p>&copy; 2025 Jasmine Software Solutions. All rights reserved.</p>
    </div>
</div>
</body>
</html>
`;
}

export function renderInvitationEmail({ acceptUrl, tenantName }: { acceptUrl: string; tenantName: string }): string {
  return `
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>You're Invited</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            background-color: #f4f4f4;
            margin: 0;
            padding: 0;
        }
        .email-container {
            max-width: 600px;
            margin: 50px auto;
            background: #ffffff;
            border: 1px solid #ddd;
            border-radius: 5px;
            box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
            overflow: hidden;
        }
        .email-header {
            background-color: #6f42c1;
            color: #ffffff;
            text-align: center;
            padding: 20px;
        }
        .email-body {
            padding: 30px;
            line-height: 1.6;
            color: #333333;
        }
        .email-body h1 {
            font-size: 24px;
            margin: 0 0 20px;
        }
        .email-footer {
            text-align: center;
            padding: 20px;
            font-size: 12px;
            color: #999999;
            border-top: 1px solid #ddd;
        }
    </style>
</head>
<body>
<div class="email-container">
    <div class="email-header">
        <h1>You're Invited</h1>
    </div>
    <div class="email-body">
        <p>Hi there,</p>
        <p>You've been invited to join <strong>${escapeHtml(tenantName)}</strong> on Jasmine Identity.</p>
        <p>Click the link below to accept the invitation:</p>
        <p><a href="${escapeAttribute(acceptUrl)}">Accept Invitation</a></p>
        <p>If you did not expect this invitation, please ignore this email.</p>
        <p>Thanks,<br>The JSS Team</p>
    </div>
    <div class="email-footer">
        <p>&copy; 2025 Jasmine Software Solutions. All rights reserved.</p>
    </div>
</div>
</body>
</html>
`;
}

export function renderCredentialStuffingEmail(resetUrl: string): string {
  return `
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Security Alert</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            background-color: #f4f4f4;
            margin: 0;
            padding: 0;
        }
        .email-container {
            max-width: 600px;
            margin: 50px auto;
            background: #ffffff;
            border: 1px solid #ddd;
            border-radius: 5px;
            box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
            overflow: hidden;
        }
        .email-header {
            background-color: #e74c3c;
            color: #ffffff;
            text-align: center;
            padding: 20px;
        }
        .email-body {
            padding: 30px;
            line-height: 1.6;
            color: #333333;
        }
        .email-body h1 {
            font-size: 24px;
            margin: 0 0 20px;
        }
        .email-footer {
            text-align: center;
            padding: 20px;
            font-size: 12px;
            color: #999999;
            border-top: 1px solid #ddd;
        }
        .email-footer a {
            color: #007bff;
            text-decoration: none;
        }
        .btn-reset {
            display: inline-block;
            padding: 12px 24px;
            background-color: #e74c3c;
            color: #ffffff;
            text-decoration: none;
            border-radius: 4px;
            margin: 20px 0;
        }
    </style>
</head>
<body>
<div class="email-container">
    <div class="email-header">
        <h1>Security Alert</h1>
    </div>
    <div class="email-body">
        <p>Hi there,</p>
        <p>We detected that the password you used to sign in has appeared in known data breaches. As a precaution, <strong>we have protected your account</strong> by signing you out of all sessions and removing your current password.</p>
        <p>You will need to set a new password to continue using your account. Please use a strong, unique password that you do not use on any other service.</p>
        <a class="btn-reset" href="${escapeAttribute(resetUrl)}">Set a New Password</a>
        <p>If you have any questions or need assistance, please contact our support team.</p>
        <p>Thanks,<br>The JSS Team</p>
    </div>
    <div class="email-footer">
        <p>If you have any issues, feel free to contact our support team.</p>
        <p>&copy; 2025 Jasmine Software Solutions. All rights reserved.</p>
    </div>
</div>
</body>
</html>
`;
}
