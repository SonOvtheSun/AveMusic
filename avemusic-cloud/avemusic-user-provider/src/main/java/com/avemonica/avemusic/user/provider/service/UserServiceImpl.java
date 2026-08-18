package com.avemonica.avemusic.user.provider.service;

import com.avemonica.avemusic.common.security.UserRole;
import com.avemonica.avemusic.user.api.dto.AuthModels;
import com.avemonica.avemusic.user.api.dto.AuthModels.AuthUser;
import com.avemonica.avemusic.user.api.dto.AuthModels.PasswordLoginRequest;
import com.avemonica.avemusic.user.api.dto.AuthModels.PhoneLoginRequest;
import com.avemonica.avemusic.user.api.dto.AuthModels.RegisterRequest;
import com.avemonica.avemusic.user.api.dto.AuthModels.SendSmsCodeRequest;
import com.avemonica.avemusic.user.api.dto.UserManagementModels;
import com.avemonica.avemusic.user.api.enums.UserErrorCode;
import com.avemonica.avemusic.user.api.service.UserService;
import com.avemonica.avemusic.user.provider.entity.UserDO;
import com.avemonica.avemusic.user.provider.mapper.PermissionMapper;
import com.avemonica.avemusic.user.provider.mapper.UserMapper;
import com.avemonica.minirpc.core.exception.RpcBusinessException;
import com.avemonica.minirpc.spring.annotation.MiniRpcService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@MiniRpcService(
        interfaceClass = UserService.class,
        group = "user",
        version = "1.0.0"
)
public class UserServiceImpl implements UserService {

    private static final System.Logger LOGGER =
            System.getLogger(
                    UserServiceImpl.class.getName()
            );

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^1[3-9]\\d{9}$");

    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[\\p{L}\\p{N}_-]{3,32}$");

    private static final SecureRandom RANDOM =
            new SecureRandom();

    private static final Duration CODE_TTL =
            Duration.ofMinutes(5);

    private static final Duration SEND_INTERVAL =
            Duration.ofSeconds(60);

    private static final String CODE_PREFIX =
            "auth:sms:code:";

    private static final String RATE_PREFIX =
            "auth:sms:rate:";

    private static final String FAIL_PREFIX =
            "auth:sms:fail:";

    /**
     * 验证码正确时才原子删除，避免同一验证码被重复使用。
     */
    private static final DefaultRedisScript<Long>
            VERIFY_CODE_SCRIPT = new DefaultRedisScript<>(
            """
            local current = redis.call('GET', KEYS[1])

            if current and current == ARGV[1] then
                redis.call('DEL', KEYS[1])
                return 1
            end

            return 0
            """,
            Long.class
    );

    private final UserMapper userMapper;
    private final PermissionMapper permissionMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    @Value("${avemusic.sms.mock:true}")
    private boolean mockSms;

    public UserServiceImpl(
            UserMapper userMapper,
            PermissionMapper permissionMapper,
            PasswordEncoder passwordEncoder,
            StringRedisTemplate redisTemplate
    ) {
        this.userMapper = userMapper;
        this.permissionMapper = permissionMapper;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void sendSmsCode(
            SendSmsCodeRequest request
    ) {
        if (request == null || request.purpose() == null) {
            throw business(
                    UserErrorCode.INVALID_PARAMETER
            );
        }

        String phone = normalizePhone(request.phone());

        String rateKey = redisKey(
                RATE_PREFIX,
                request.purpose(),
                phone
        );

        Boolean firstRequest =
                redisTemplate.opsForValue()
                        .setIfAbsent(
                                rateKey,
                                "1",
                                SEND_INTERVAL
                        );

        if (!Boolean.TRUE.equals(firstRequest)) {
            throw business(
                    UserErrorCode.SMS_TOO_FREQUENT
            );
        }

        String code = String.format(
                "%06d",
                RANDOM.nextInt(1_000_000)
        );

        String codeKey = redisKey(
                CODE_PREFIX,
                request.purpose(),
                phone
        );

        redisTemplate.opsForValue().set(
                codeKey,
                code,
                CODE_TTL
        );

        if (mockSms) {
            LOGGER.log(
                    System.Logger.Level.INFO,
                    """
                    [开发环境短信验证码]
                    phone={0}, purpose={1}, code={2}
                    """,
                    phone,
                    request.purpose(),
                    code
            );
        }

        /*
         * 正式短信接入时：
         *
         * 1. 在这里调用短信平台；
         * 2. 发送失败时删除 codeKey 和 rateKey；
         * 3. mockSms=false 时禁止在日志中输出验证码。
         */
    }

    @Override
    @Transactional
    public AuthUser register(RegisterRequest request) {
        if (request == null) {
            throw business(
                    UserErrorCode.INVALID_PARAMETER
            );
        }

        String username =
                normalizeUsername(request.username());

        String phone =
                normalizePhone(request.phone());

        validatePassword(request.password());

        if (existsByUsername(username)) {
            throw business(
                    UserErrorCode.USERNAME_ALREADY_EXISTS
            );
        }

        if (existsByPhone(phone)) {
            throw business(
                    UserErrorCode.PHONE_ALREADY_EXISTS
            );
        }

        verifyAndConsumeCode(
                AuthModels.SmsPurpose.REGISTER,
                phone,
                request.code()
        );

        UserDO user = new UserDO();
        user.setUsername(username);
        user.setPhone(phone);
        user.setPasswordHash(
                passwordEncoder.encode(
                        request.password()
                )
        );
        user.setGender(0);
        user.setStatus(1);

        /*
         * 自主注册只能成为普通用户。
         * role 不能来自前端注册参数。
         */
        user.setRole(UserRole.USER);
        user.setRealNameInfoId(null);
        user.setArtistId(null);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            /*
             * 前面的 exists 校验只能改善提示体验，
             * 数据库唯一索引才是最终并发保障。
             */
            if (existsByUsername(username)) {
                throw business(
                        UserErrorCode.USERNAME_ALREADY_EXISTS
                );
            }

            if (existsByPhone(phone)) {
                throw business(
                        UserErrorCode.PHONE_ALREADY_EXISTS
                );
            }

            throw exception;
        }

        return toAuthUser(user);
    }

    @Override
    public AuthUser authenticate(
            PasswordLoginRequest request
    ) {
        if (request == null
                || request.account() == null
                || request.account().isBlank()
                || request.password() == null) {

            throw business(
                    UserErrorCode.INVALID_CREDENTIALS
            );
        }

        String account = request.account().trim();

        UserDO user = userMapper.selectOne(
                new LambdaQueryWrapper<UserDO>()
                        .and(wrapper ->
                                wrapper
                                        .eq(
                                                UserDO::getUsername,
                                                account
                                        )
                                        .or()
                                        .eq(
                                                UserDO::getPhone,
                                                account
                                        )
                        )
                        .last("LIMIT 1")
        );

        if (user == null
                || !passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        )) {

            throw business(
                    UserErrorCode.INVALID_CREDENTIALS
            );
        }

        ensureEnabled(user);

        return toAuthUser(user);
    }


    @Override
    public AuthUser loginByPhone(
            PhoneLoginRequest request
    ) {
        if (request == null) {
            throw business(
                    UserErrorCode.INVALID_PARAMETER
            );
        }

        String phone =
                normalizePhone(request.phone());

        verifyAndConsumeCode(
                AuthModels.SmsPurpose.LOGIN,
                phone,
                request.code()
        );

        UserDO user = findByPhone(phone);

        if (user == null) {
            throw business(
                    UserErrorCode.PHONE_NOT_REGISTERED
            );
        }

        ensureEnabled(user);

        return toAuthUser(user);
    }

    private void verifyAndConsumeCode(
            AuthModels.SmsPurpose purpose,
            String phone,
            String inputCode
    ) {
        if (inputCode == null
                || !inputCode.matches("\\d{6}")) {
            throw business(
                    UserErrorCode.INVALID_SMS_CODE
            );
        }

        String failKey = redisKey(
                FAIL_PREFIX,
                purpose,
                phone
        );

        String failText =
                redisTemplate.opsForValue().get(failKey);

        if (failText != null
                && Integer.parseInt(failText) >= 5) {
            throw business(
                    UserErrorCode.INVALID_SMS_CODE
            );
        }

        String codeKey = redisKey(
                CODE_PREFIX,
                purpose,
                phone
        );

        Long verified = redisTemplate.execute(
                VERIFY_CODE_SCRIPT,
                List.of(codeKey),
                inputCode
        );

        if (!Long.valueOf(1).equals(verified)) {
            Long failures =
                    redisTemplate.opsForValue()
                            .increment(failKey);

            if (Long.valueOf(1).equals(failures)) {
                redisTemplate.expire(
                        failKey,
                        CODE_TTL
                );
            }

            throw business(
                    UserErrorCode.INVALID_SMS_CODE
            );
        }

        redisTemplate.delete(failKey);
    }

    private boolean existsByUsername(String username) {
        return userMapper.exists(
                new LambdaQueryWrapper<UserDO>()
                        .eq(UserDO::getUsername, username)
        );
    }

    private boolean existsByPhone(String phone) {
        return userMapper.exists(
                new LambdaQueryWrapper<UserDO>()
                        .eq(UserDO::getPhone, phone)
        );
    }

    private UserDO findByPhone(String phone) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<UserDO>()
                        .eq(UserDO::getPhone, phone)
                        .last("LIMIT 1")
        );
    }

    private static void ensureEnabled(UserDO user) {
        if (!Integer.valueOf(1).equals(
                user.getStatus()
        )) {
            throw business(
                    UserErrorCode.USER_DISABLED
            );
        }
    }

    private AuthUser toAuthUser(UserDO user) {
        UserRole role =
                user.getRole() == null
                        ? UserRole.USER
                        : user.getRole();

        List<String> authorities =
                new ArrayList<>();

        authorities.add(
                role.authority()
        );

        authorities.addAll(
                permissionMapper.selectByRole(
                        role.name()
                )
        );

        return new AuthUser(
                String.valueOf(user.getId()),
                user.getUsername(),
                user.getAvatarUrl(),
                role,
                authorities.stream()
                        .distinct()
                        .toList()
        );
    }

    private static String normalizeUsername(
            String username
    ) {
        if (username == null) {
            throw business(
                    UserErrorCode.INVALID_PARAMETER
            );
        }

        String value = username.trim();

        if (!USERNAME_PATTERN.matcher(value).matches()) {
            throw new RpcBusinessException(
                    UserErrorCode.INVALID_PARAMETER,
                    "用户名只能包含中文、字母、数字、下划线或短横线，长度为3到32位"
            );
        }

        return value;
    }

    private static String normalizePhone(String phone) {
        if (phone == null) {
            throw business(
                    UserErrorCode.INVALID_PARAMETER
            );
        }

        String value = phone.trim();

        if (!PHONE_PATTERN.matcher(value).matches()) {
            throw new RpcBusinessException(
                    UserErrorCode.INVALID_PARAMETER,
                    "手机号格式不正确"
            );
        }

        return value;
    }

    private static void validatePassword(
            String password
    ) {
        if (password == null
                || password.length() < 8
                || password.length() > 64) {

            throw new RpcBusinessException(
                    UserErrorCode.INVALID_PARAMETER,
                    "密码长度必须为8到64位"
            );
        }
    }

    private static String redisKey(
            String prefix,
            AuthModels.SmsPurpose purpose,
            String phone
    ) {
        return prefix
                + purpose.name().toLowerCase()
                + ":"
                + phone;
    }

    private static RpcBusinessException business(
            UserErrorCode errorCode
    ) {
        return new RpcBusinessException(errorCode);
    }
}