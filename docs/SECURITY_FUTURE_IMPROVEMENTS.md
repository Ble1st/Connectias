# Security Future Improvements

This document lists security features that are currently not implemented but are recommended for future enhancements. These features were identified during security scanning but are not critical for the current release.

## Overview

The following security features are documented as potential future improvements to enhance the security posture of the Connectias application:

1. SSL/TLS Certificate Pinning
2. Certificate Transparency
3. Screenshot Prevention
4. SafetyNet/Play Integrity API
5. Tapjacking Prevention

## 1. SSL/TLS Certificate Pinning

**Status:** Not Implemented  
**Priority:** Medium  
**CWE:** CWE-295: Improper Certificate Validation  
**MASVS:** MSTG-NETWORK-4  
**OWASP Mobile:** M3: Insecure Communication

### Description

SSL/TLS certificate pinning prevents Man-in-the-Middle (MITM) attacks by ensuring that the app only accepts connections from servers with specific certificates or public keys.

### Current State

- The app uses standard TLS/SSL connections
- No certificate pinning is currently implemented
- Network security configuration is present but doesn't include pinning

### Implementation Notes

- Consider using OkHttp CertificatePinner (already a dependency)
- Implement pinning for critical API endpoints
- Ensure proper certificate rotation mechanism
- Test thoroughly to avoid breaking connections after certificate updates

### References

- [OWASP Mobile Testing Guide - Certificate Pinning](https://github.com/MobSF/owasp-mstg/blob/master/Document/0x05g-Testing-Network-Communication.md#testing-custom-certificate-stores-and-certificate-pinning-mstg-network-4)

## 2. Certificate Transparency

**Status:** Not Implemented  
**Priority:** Low  
**CWE:** CWE-295: Improper Certificate Validation  
**MASVS:** MSTG-NETWORK-4  
**OWASP Mobile:** M3: Insecure Communication

### Description

Certificate Transparency helps detect SSL certificates that have been mistakenly issued by a certificate authority or maliciously acquired from an otherwise unimpeachable certificate authority.

### Current State

- Certificate Transparency is not enforced
- Standard certificate validation is used

### Implementation Notes

- Requires integration with Certificate Transparency logs
- Can be implemented alongside certificate pinning
- Consider using libraries that support CT validation

### References

- [Certificate Transparency](https://certificate.transparency.dev/)

## 3. Screenshot Prevention

**Status:** Not Implemented  
**Priority:** Low  
**CWE:** CWE-200: Information Exposure  
**MASVS:** MSTG-STORAGE-9  
**OWASP Mobile:** M1: Improper Platform Usage

### Description

Prevents screenshots from being taken of sensitive screens, including protection against Recent Task History and Now On Tap features.

### Current State

- No screenshot prevention is implemented
- Sensitive data may be exposed through screenshots

### Implementation Notes

- Use `Window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, ...)` for sensitive Activities/Fragments
- Consider implementing for:
  - Password input screens
  - Security settings screens
  - Any screens displaying sensitive user data
- Note: This doesn't prevent all forms of screen capture (e.g., external cameras)

### References

- [Android Developer - Secure Window](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_SECURE)

## 4. SafetyNet/Play Integrity API

**Status:** Not Implemented  
**Priority:** Medium  
**CWE:** CWE-353: Missing Support for Integrity Check  
**MASVS:** MSTG-RESILIENCE-1  
**OWASP Mobile:** M9: Reverse Engineering

### Description

SafetyNet (deprecated) / Play Integrity API provides cryptographically-signed attestation, assessing the device's integrity. This helps ensure that servers are interacting with genuine, unmodified devices.

### Current State

- No device integrity checks are performed
- RASP (Runtime Application Self-Protection) is implemented for root/emulator detection
- No server-side attestation

### Implementation Notes

- **Important:** SafetyNet is deprecated, use Play Integrity API instead
- Implement server-side attestation verification
- Integrate with existing RASP system
- Consider for:
  - Critical API endpoints
  - Payment processing
  - Authentication flows

### References

- [Play Integrity API](https://developer.android.com/google/play/integrity)
- [SafetyNet Migration Guide](https://developer.android.com/training/safetynet/deprecation-timeline)

## 5. Tapjacking Prevention

**Status:** Not Implemented  
**Priority:** Low  
**CWE:** CWE-200: Information Exposure  
**MASVS:** MSTG-AUTH-10  
**OWASP Mobile:** M1: Improper Platform Usage

### Description

Tapjacking (UI Redressing) attacks overlay malicious UI elements on top of legitimate app screens to trick users into performing unintended actions.

### Current State

- No tapjacking prevention is implemented
- Activities may be vulnerable to overlay attacks

### Implementation Notes

- Use `View.setFilterTouchesWhenObscured(true)` for sensitive UI elements
- Implement `onFilterTouchEvent()` to detect overlay attacks
- Consider using `Window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, ...)`
- Focus on:
  - Login screens
  - Permission dialogs
  - Payment screens
  - Critical action buttons

### References

- [Android Developer - Filter Touches](https://developer.android.com/reference/android/view/View#setFilterTouchesWhenObscured(boolean))

## Implementation Priority

Based on security impact and implementation complexity:

1. **High Priority:**
   - SSL/TLS Certificate Pinning (Medium complexity, High security impact)
   - Play Integrity API (Medium complexity, High security impact)

2. **Medium Priority:**
   - Screenshot Prevention (Low complexity, Medium security impact)
   - Tapjacking Prevention (Low complexity, Medium security impact)

3. **Low Priority:**
   - Certificate Transparency (Medium complexity, Low security impact)

## Notes

- All features should be thoroughly tested before production deployment
- Consider user experience impact when implementing security features
- Some features may require server-side support (e.g., Play Integrity API)
- Regular security audits should review these features for implementation status

## Last Updated

2025-02-12 - Initial documentation based on mobsfscan security scan results
