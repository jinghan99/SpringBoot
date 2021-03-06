package com.common.core.shiro;

import com.common.config.Global;
import com.common.core.shiro.session.RedisSessionDao;
import com.common.util.Encodes;
import com.common.util.PasswordUtil;
import com.common.util.StringUtils;
import com.google.common.collect.Lists;
import com.modules.sys.entity.Menu;
import com.modules.sys.entity.Role;
import com.modules.sys.entity.User;
import com.modules.sys.service.MenuService;
import com.modules.sys.service.RoleService;
import com.modules.sys.service.UserService;
import org.apache.shiro.authc.*;
import org.apache.shiro.authc.credential.HashedCredentialsMatcher;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.util.ByteSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

@Service
public class SystemAuthorizingRealm extends AuthorizingRealm {

    @Autowired
    private UserService userService;
    @Autowired
    private RoleService roleService;
    @Autowired
    private MenuService menuService;


    /**
     * 认证回调函数，登录信息和用户验证信息验证
     */
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken authToken) throws AuthenticationException {
        System.out.println("登录认证，登录信息和用户验证信息验证 --> doGetAuthenticationInfo");

        // /获取用户名和密码的令牌
        UsernamePasswordToken usernamePasswordToken = (UsernamePasswordToken) authToken;

        User user = userService.getByLoginName(usernamePasswordToken.getUsername());
        if (user == null) {
            throw new AccountException("msg:帐号或密码不正确！");
        } else if (Global.NO.equals(user.getLoginFlag())) {
            throw new DisabledAccountException("msg:帐号已经禁止登录！");
        } else {
            // FIXME 更新登录时间 last login time

        }

        // 密码加密
        byte[] salt = Encodes.decodeHex(user.getPassword().substring(0, 16));
        // 登录认证
        return new SimpleAuthenticationInfo(new Principal(user), // 用户名
                                            user.getPassword().substring(16), // 密码
                                            ByteSource.Util.bytes(salt), // salt = 加密之后的密码
                                            getName() // realm name
        );
    }


    /**
     * 授权查询回调函数
     */
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {

        // 获取当前已经登录的用户
        Principal principal = (Principal) getAvailablePrincipal(principals);
        System.out.println("登录之后访问权限控制的菜单或内容时进行授权--> doGetAuthorizationInfo " + principal.getLoginName());

        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();

        // 根据 userId 查询对应的角色
        List<Role> roles = roleService.findRoleByUserId(principal.getId());
        // 角色集合
        List<String> rolesENname = Lists.newArrayList();
        for (Role role : roles) {
            if(null != role){
                rolesENname.add(role.getEnname());
            }
        }
        info.addRoles(rolesENname);

        // 根据 userId 查询对应的菜单和菜单的权限
        List<Menu> menus = menuService.findMenuByUserId(principal.getId());
        // 菜单权限集合
        List<String> permissions = Lists.newArrayList();
        buildPermissions(menus, permissions);
        info.addStringPermissions(permissions);

        return info;
    }

    private void buildPermissions(List<Menu> menus, List<String> permissions) {
        for(Menu menu : menus){
            if (menu.getChildMenus() != null && menu.getChildMenus().size() > 0) {
                buildPermissions(menu.getChildMenus(), permissions);
            }

            if (StringUtils.isNotBlank(menu.getPermission())) {
                permissions.add(menu.getPermission());
            }
        }
    }

    /**
     * @PostConstruct web 容器启动的时候在 servlet 加载之后在 init 方法之前执行
     * 设定密码校验的Hash算法与迭代次数
     */
    @PostConstruct
    public void initCredentialsMatcher() {
        HashedCredentialsMatcher matcher = new HashedCredentialsMatcher(PasswordUtil.HASH_ALGORITHM_SHA1);
        matcher.setHashIterations(PasswordUtil.HASH_INTERATIONS);
        setCredentialsMatcher(matcher);
    }


    @Override
    protected void clearCachedAuthorizationInfo(PrincipalCollection principals) {
        super.clearCachedAuthorizationInfo(principals);
    }

    /**
     * 授权用户信息
     */
    public static class Principal implements Serializable {

        private String id; // 编号
        private String loginName; // 登录名
        private String name; // 姓名


        public Principal(User user) {
            this.id = user.getId();
            this.loginName = user.getLoginName();
            this.name = user.getName();
        }


        public String getId() {
            return id;
        }

        public String getLoginName() {
            return loginName;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return id;
        }
    }
}