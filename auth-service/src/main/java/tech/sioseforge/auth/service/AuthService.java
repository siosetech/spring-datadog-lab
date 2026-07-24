package tech.sioseforge.auth.service;

import tech.sioseforge.auth.domain.view.AuthResponseVO;
import tech.sioseforge.auth.domain.view.LoginRequestVO;

import tech.sioseforge.auth.domain.view.RegisterRequestVO;

public interface AuthService {
    AuthResponseVO login(LoginRequestVO request);
    AuthResponseVO register(RegisterRequestVO request);
}
