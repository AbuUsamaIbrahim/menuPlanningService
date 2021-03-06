package com.mahadi.restapi.service.impl;

import com.mahadi.restapi.dto.ResetPasswordDto;
import com.mahadi.restapi.dto.Response;
import com.mahadi.restapi.dto.UserDto;
import com.mahadi.restapi.model.Role;
import com.mahadi.restapi.model.User;
import com.mahadi.restapi.repository.RoleRepository;
import com.mahadi.restapi.repository.UserRepository;
import com.mahadi.restapi.service.UserService;
import com.mahadi.restapi.service.UtilityService;
import com.mahadi.restapi.util.ResponseBuilder;
import com.mahadi.restapi.util.UserStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.transaction.Transactional;
import java.util.*;

@Service("userService")
public class UserServiceImpl implements UserService {
    private static final Logger logger = LogManager.getLogger(UserServiceImpl.class.getName());
    private final UserRepository userRepository;
    private final String root = "User";
    private final ModelMapper modelMapper;
    private final PasswordEncoder passwordEncoder;
    private final UtilityService utilityService;
    private final RoleRepository roleRepository;
    @PersistenceContext
    private EntityManager entityManager;


    public UserServiceImpl(UserRepository userRepository, ModelMapper modelMapper, UtilityService utilityService, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.modelMapper = modelMapper;
        this.passwordEncoder = passwordEncoder;
        this.utilityService = utilityService;
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional
    public Response create(UserDto userDto) {
        User user = modelMapper.map(userDto, User.class);
        if (checkDuplicateEmailBeforeInsert(user.getEmail())) {
            return ResponseBuilder.getFailResponse(HttpStatus.BAD_REQUEST, "This email is already exist.");
        }
        List<Role> roleList = new ArrayList<>();
        Role role = new Role();
//            role.setLevel(1);
        role.setId(Long.valueOf("2"));
        role.setName("ROLE_USER");
        role.setIsActive(true);
        role = roleRepository.save(role);
        roleList.add(role);
        user.setRoles(roleList);
        user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        user.setStatus(UserStatus.ACTIVE.name());
        Response response = utilityService.getCreateResponse(user, userRepository);
        /*if (response != null && response.getStatusCode() == 201) {
            createSuccessMail(user);
        }*/
        return response;
    }

//    private String getToken() {
//        String token = UUID.randomUUID().toString();
//        VerificationToken verificationToken = verificationTokenRepository.findByToken(token);
//        if(verificationToken != null){
//            return getToken();
//        }
//        return token;
//    }

    /*@Override
    public void deleteAllExpiredToken(){
        List<VerificationToken> verificationTokenList = verificationTokenRepository.findAllByExpiryDateBeforeAndIsActiveTrue(new Date());
        List<VerificationToken> deletedVerificationTokenList = new ArrayList<>();
        verificationTokenList.forEach(verificationToken -> {
            verificationToken.setIsActive(false);
            deletedVerificationTokenList.add(verificationToken);
        });
        verificationTokenRepository.saveAll(deletedVerificationTokenList);
    }*/

    private Boolean checkDuplicateEmailBeforeInsert(String email) {
        List<User> userList = userRepository.findAllByEmailAndIsActiveTrue(email);
        return userList.size() != 0;
    }

    @Override
    @Transactional
    public Response update(Long id, UserDto userDto) {
        Response notFoundFailureResponse = utilityService.getNullResponse(userRepository, id);
        if (notFoundFailureResponse != null) {
            return notFoundFailureResponse;
        }
        try {
            User user = utilityService.getById(userRepository, id);
            user.setPassword(passwordEncoder.encode(userDto.getPassword()));
            return utilityService.getUpdateResponse(user, userDto, userRepository);
        } catch (NullPointerException e) {
            logger.error(e.getMessage());
            return ResponseBuilder.getFailResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            logger.error(e.getMessage());
            return ResponseBuilder.getFailResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @Override
    @Transactional
    public Response delete(Long id) {
        Response notFoundFailureResponse = utilityService.getNullResponse(userRepository, id);
        if (notFoundFailureResponse != null) {
            return notFoundFailureResponse;
        }
        try {
            User user = utilityService.getById(userRepository, id);
            List<Role> roleList = user.getRoles();
            final Boolean[] isAdmin = {false};
            if (roleList != null && roleList.size() > 0) {
                roleList.forEach(role -> {
                    role = roleRepository.findByIdAndIsActiveTrue(role.getId());
                    if (role.getName().equals("ROLE_ADMIN")) {
                        isAdmin[0] = true;
                    }
                });
            }
            if (isAdmin[0]) {
                return ResponseBuilder.getFailResponse(HttpStatus.BAD_REQUEST, "Admin User Can't be deleted");
            }
            return utilityService.deleteEntityResponse(user, userRepository);
        } catch (NullPointerException e) {
            logger.error(e.getMessage());
            return ResponseBuilder.getFailResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            logger.error(e.getMessage());
            return ResponseBuilder.getFailResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @Override
    @Transactional
    public Response get(Long id) {
        Response notFoundFailureResponse = utilityService.getNullResponse(userRepository, id);
        if (notFoundFailureResponse != null) {
            return notFoundFailureResponse;
        }
        try {
            User user = utilityService.getById(userRepository, id);
            UserDto userDto = modelMapper.map(user, UserDto.class);
            return utilityService.getGetResponse(userDto, root);
        } catch (NullPointerException e) {
            logger.error(e.getMessage());
            return ResponseBuilder.getFailResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            logger.error(e.getMessage());
            return ResponseBuilder.getFailResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @Override
    @Transactional
    public Response getAll(Pageable pageable, boolean isExport, String search, String status) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> criteriaQuery = criteriaBuilder.createQuery(User.class);
        Root<User> rootEntity = criteriaQuery.from(User.class);

        addPredicates(criteriaBuilder, criteriaQuery, rootEntity, search);

        TypedQuery<User> typedQuery = entityManager.createQuery(criteriaQuery);
        return getAllResponse(criteriaQuery, typedQuery, pageable, isExport);
    }

    private Response getAllResponse(CriteriaQuery<User> criteriaQuery, TypedQuery<User> typedQuery, Pageable pageable, boolean isExport) {
        if (utilityService.getAllFailureResponse(typedQuery, isExport, pageable, root) != null) {
            return utilityService.getAllFailureResponse(typedQuery, isExport, pageable, root);
        }
        long totalRows = this.getTotalRows(criteriaQuery);
        Page<User> countries = utilityService.getAllPage(typedQuery, pageable);
        return utilityService.getAllSuccessResponse(totalRows, this.getResponseDtoList(countries), root);
    }

    private long getTotalRows(CriteriaQuery<User> criteriaQuery) {
        TypedQuery<User> typedQuery = entityManager.createQuery(criteriaQuery);
        return typedQuery.getResultList().size();
    }

    /*@Override
    public Response createForgotPasswordRequest(ForgotPasswordRequestDto forgotPasswordRequestDto) {
        String email = forgotPasswordRequestDto.getEmail();
        User user = userRepository.findByEmailAndIsActiveTrue(email);
        if(user != null){
            VerificationToken verificationToken = new VerificationToken();
            String token = getToken();
            verificationToken.setToken(token);
            verificationToken.setExpiryDate(DateUtils.calculateExpiryDate(verificationExpireMinutes));
            verificationToken.setIsActive(true);
            verificationToken.setUser(user);
            verificationToken = verificationTokenRepository.save(verificationToken);
            if(verificationToken != null){
                forgotPasswordMail(email, user.getName(), token);
                return ResponseBuilder.getSuccessResponse(HttpStatus.OK, null, "Password Confirmed");
            }
            return ResponseBuilder.getFailResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error");
        }
        return ResponseBuilder.getFailResponse(HttpStatus.BAD_REQUEST, "Requested user not found");
    }*/

    /*@Override
    public Response changeForgottenPassword(ForgotPasswordDto forgotPasswordDto) {
        if(!forgotPasswordDto.getNewPassword().equals(forgotPasswordDto.getConfirmPassword())){
            return ResponseBuilder.getFailResponse(HttpStatus.BAD_REQUEST, "new password and confirm password not matched");
        }
        String token  = forgotPasswordDto.getToken();
        VerificationToken verificationToken = verificationTokenRepository.findByTokenAndIsActiveTrue(token);
        if(verificationToken != null){
            User user = userRepository.findByIdAndIsActiveTrue(verificationToken.getUser().getId());
            if(user != null){
                user.setPassword(passwordEncoder.encode(forgotPasswordDto.getNewPassword()));
                user = userRepository.save(user);
                if(user != null){
                    return ResponseBuilder.getSuccessResponse(HttpStatus.OK, null,"Password Changed successfully");
                }
                return ResponseBuilder.getFailResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error");
            }
            return ResponseBuilder.getFailResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error");
        }
        return ResponseBuilder.getFailResponse(HttpStatus.BAD_REQUEST, "Link expired. Please try again.");
    }*/

    @Override
    @Transactional
    public Response resetPassword(ResetPasswordDto resetPasswordDto) {
        User user = utilityService.getAuthenticatedUser();
        if (user != null) {
            if (passwordEncoder.matches(resetPasswordDto.getPassword(), user.getPassword()) && resetPasswordDto.getNewPassword().equals(resetPasswordDto.getConfirmPassword())) {
                user.setPassword(passwordEncoder.encode(resetPasswordDto.getNewPassword()));
                user = userRepository.save(user);
                if (user != null) {
                    return ResponseBuilder.getSuccessResponse(HttpStatus.OK, null, "Password Changed successfully");
                }
                return ResponseBuilder.getFailResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error");
            }
            if (passwordEncoder.matches(resetPasswordDto.getPassword(), user.getPassword())) {
                return ResponseBuilder.getFailResponse(HttpStatus.BAD_REQUEST, "Current password not matched");
            }
            return ResponseBuilder.getFailResponse(HttpStatus.BAD_REQUEST, "New password and confirm password not matched");
        }
        return ResponseBuilder.getFailResponse(HttpStatus.UNAUTHORIZED, "Unauthorized access. Please communicate with system admin");
    }

    private void addPredicates(CriteriaBuilder criteriaBuilder, CriteriaQuery<User> criteriaQuery, Root<User> rootEntity, String search) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(criteriaBuilder.and(criteriaBuilder.isTrue(rootEntity.<Boolean>get("isActive")), criteriaBuilder.equal(rootEntity.<String>get("status"), "ACTIVE")));

        if (search != null && search.trim().length() > 0) {

            Predicate pLike = criteriaBuilder.or(
                    criteriaBuilder.like(rootEntity.<String>get("username"), "%" + search + "%"),
                    criteriaBuilder.like(rootEntity.<String>get("email"), "%" + search + "%"));
            predicates.add(pLike);
        }

        if (predicates.isEmpty()) {
            logger.error("predicates isEmpty ");
            criteriaQuery.select(rootEntity);
        } else {
            logger.error("predicates is not Empty ");
            criteriaQuery.select(rootEntity).where(predicates.toArray(new Predicate[predicates.size()]));
        }
    }


    private List<UserDto> getResponseDtoList(Page<User> users) {
        List<UserDto> responseDtos = new ArrayList<>();
        users.forEach(user -> {
            UserDto userResponseDto = modelMapper.map(user, UserDto.class);
            responseDtos.add(userResponseDto);
        });
        return responseDtos;
    }

    @Override
    @Transactional
    public User getUserByEmailOrUserName(String emailOrUserName) {
        return userRepository.findByUsernameOrEmailAndIsActiveTrue(emailOrUserName, emailOrUserName).get();
    }
}
