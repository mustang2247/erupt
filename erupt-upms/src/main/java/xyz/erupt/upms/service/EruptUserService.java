package xyz.erupt.upms.service;

import com.google.gson.reflect.TypeToken;
import eu.bitwalker.useragentutils.UserAgent;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import xyz.erupt.core.config.EruptAppProp;
import xyz.erupt.core.config.EruptProp;
import xyz.erupt.core.view.EruptApiModel;
import xyz.erupt.jpa.dao.EruptDao;
import xyz.erupt.upms.base.LoginModel;
import xyz.erupt.upms.config.EruptUpmsConfig;
import xyz.erupt.upms.constant.EruptReqHeaderConst;
import xyz.erupt.upms.constant.SessionKey;
import xyz.erupt.upms.model.EruptMenu;
import xyz.erupt.upms.model.EruptUser;
import xyz.erupt.upms.model.log.EruptLoginLog;
import xyz.erupt.upms.util.IpUtil;
import xyz.erupt.upms.util.MD5Utils;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.http.HttpServletRequest;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * @author liyuepeng
 * @date 2018-12-13.
 */
@Service
public class EruptUserService {

    @PersistenceContext
    private EntityManager entityManager;

    @Resource
    private EruptSessionService sessionService;

    @Resource
    private HttpServletRequest request;

    @Resource
    private EruptDao eruptDao;

    @Resource
    private EruptAppProp eruptAppProp;

    @Resource
    private EruptProp eruptProp;

    @Resource
    private EruptUpmsConfig eruptUpmsConfig;

    @Transactional
    public void saveLoginLog(EruptUser user) {
        UserAgent userAgent = UserAgent.parseUserAgentString(request.getHeader("User-Agent"));
        EruptLoginLog loginLog = new EruptLoginLog();
        loginLog.setEruptUser(user);
        loginLog.setLoginTime(new Date());
        loginLog.setIp(IpUtil.getIpAddr(request));
        loginLog.setSystemName(userAgent.getOperatingSystem().getName());
        loginLog.setRegion(IpUtil.getCityInfo(loginLog.getIp()));
        loginLog.setBrowser(userAgent.getBrowser().getName() + " " + (userAgent.getBrowserVersion() == null ? "" : userAgent.getBrowserVersion().getMajorVersion()));
        loginLog.setDeviceType(userAgent.getOperatingSystem().getDeviceType().getName());
        entityManager.persist(loginLog);
    }

    public static final String LOGIN_ERROR_HINT = "账号或密码错误";

    public LoginModel login(String account, String pwd, String verifyCode) {
        String requestIp = IpUtil.getIpAddr(request);
        Object loginError = sessionService.get(SessionKey.LOGIN_ERROR + requestIp);
        long loginErrorCount = 0;
        if (null != loginError) {
            loginErrorCount = Long.parseLong(loginError.toString());
        }
        if (loginErrorCount >= eruptAppProp.getVerifyCodeCount()) {
            if (StringUtils.isBlank(verifyCode)) {
                return new LoginModel(false, "请填写验证码", true);
            }
            Object vc = sessionService.get(SessionKey.VERIFY_CODE + requestIp);
            sessionService.remove(SessionKey.VERIFY_CODE + requestIp);
            if (vc == null || !vc.toString().equalsIgnoreCase(verifyCode)) {
                return new LoginModel(false, "验证码不正确", true);
            }
        }
        EruptUser eruptUser = this.findEruptUserByAccount(account);
        if (null != eruptUser) {
            if (!eruptUser.getStatus()) {
                return new LoginModel(false, "账号已锁定!");
            }
            //校验IP
            if (StringUtils.isNotBlank(eruptUser.getWhiteIp())) {
                boolean isAllowIp = false;
                for (String ip : eruptUser.getWhiteIp().split("\n")) {
                    if (ip.equals(requestIp)) {
                        isAllowIp = true;
                        break;
                    }
                }
                if (!isAllowIp) {
                    return new LoginModel(false, "ip不允许访问");
                }
            }
            //校验密码
            boolean pass = false;
            {
                String digestPwd;
                if (eruptUser.getIsMd5()) {
                    digestPwd = eruptUser.getPassword();
                } else {
                    digestPwd = MD5Utils.digest(eruptUser.getPassword());
                }
                String calcPwd = MD5Utils.digest(digestPwd +
                        Calendar.getInstance().get(Calendar.DAY_OF_MONTH) + account);
                if (pwd.equalsIgnoreCase(calcPwd)) {
                    pass = true;
                }
            }
            if (pass) {
                sessionService.putByLoginExpire(SessionKey.LOGIN_ERROR + requestIp, "0");
                return new LoginModel(true, eruptUser);
            } else {
                return new LoginModel(false, LOGIN_ERROR_HINT, loginErrorCountPlus(requestIp));
            }
        } else {
            return new LoginModel(false, LOGIN_ERROR_HINT, loginErrorCountPlus(requestIp));
        }
    }

    private boolean loginErrorCountPlus(String ip) {
        Object loginError = sessionService.get(SessionKey.LOGIN_ERROR + ip);
        int loginErrorCount = 0;
        if (null != loginError) {
            loginErrorCount = Integer.parseInt(loginError.toString());
        }
        sessionService.putByLoginExpire(SessionKey.LOGIN_ERROR + ip, ++loginErrorCount + "");
        return loginErrorCount >= eruptAppProp.getVerifyCodeCount();
    }

    public LocalDateTime getExpireTime() {
        if (eruptProp.isRedisSession()) {
            return LocalDateTime.now().plusMinutes(eruptUpmsConfig.getExpireTimeByLogin());
        } else {
            return LocalDateTime.now().plusSeconds(request.getSession().getMaxInactiveInterval());
        }
    }

    @Transactional
    public EruptApiModel changePwd(String account, String pwd, String newPwd, String newPwd2) {
        if (!newPwd.equals(newPwd2)) {
            return EruptApiModel.errorNoInterceptApi("修改失败，新密码与确认密码不匹配");
        }
        EruptUser eruptUser = findEruptUserByAccount(account);
        if (eruptUser.getIsMd5()) {
            pwd = MD5Utils.digest(pwd);
            newPwd = MD5Utils.digest(newPwd);
        }
        if (eruptUser.getPassword().equals(pwd)) {
            if (newPwd.equals(eruptUser.getPassword())) {
                return EruptApiModel.errorNoInterceptApi("修改失败，新密码不能和原始密码一样");
            }
            eruptUser.setPassword(newPwd);
            entityManager.merge(eruptUser);
            return EruptApiModel.successApi();
        } else {
            return EruptApiModel.errorNoInterceptApi("密码错误");
        }
    }


    public EruptUser getCurrentEruptUser() {
        entityManager.clear();
        Long uid = this.getCurrentUid();
        return null == uid ? null : entityManager.find(EruptUser.class, uid);
    }

    public List<EruptMenu> getCurrentEruptUserMenu() {
        return sessionService.get(SessionKey.MENU + getToken(), new TypeToken<List<EruptMenu>>() {
        }.getType());
    }

    public String getToken() {
        String token = request.getHeader(EruptReqHeaderConst.ERUPT_HEADER_TOKEN);
        if (StringUtils.isBlank(token)) {
            token = request.getParameter(EruptReqHeaderConst.URL_ERUPT_PARAM_TOKEN);
        }
        return token;
    }

    public Long getCurrentUid() {
        Object uid = sessionService.get(SessionKey.USER_TOKEN + getToken());
        return null == uid ? null : Long.valueOf(uid.toString());
    }


    private EruptUser findEruptUserByAccount(String account) {
        return eruptDao.queryEntity(EruptUser.class, "account = :account",
                new HashMap<String, Object>(1) {
                    {
                        this.put("account", account);
                    }
                });
    }


}
