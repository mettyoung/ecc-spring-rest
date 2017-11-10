package com.ecc.spring_security.service;

import java.io.Serializable;
import java.util.List;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.validation.Validator;
import org.springframework.validation.Errors;

import com.ecc.spring_security.dto.UserDTO;
import com.ecc.spring_security.model.User;
import com.ecc.spring_security.dao.UserDao;
import com.ecc.spring_security.assembler.UserAssembler;
import com.ecc.spring_security.util.AssemblerUtils;
import com.ecc.spring_security.util.ValidationUtils;
import com.ecc.spring_security.util.ValidationException;

@Component("userService")
public class UserService extends AbstractService<User, UserDTO> implements Validator {
	private static final Integer MAX_CHARACTERS = 255;

	private final UserDao userDao;
	private final UserAssembler userAssembler;

	public UserService(UserDao userDao, UserAssembler userAssembler) {
		super(userDao, userAssembler);
		this.userDao = userDao;
		this.userAssembler = userAssembler;
	}

	@Override
	public boolean supports(Class clazz) {
        return clazz.isAssignableFrom(UserDTO.class);
    }

    @Override
    public void validate(Object command, Errors errors) {
    	UserDTO user = (UserDTO) command;
		ValidationUtils.testNotEmpty(user.getUsername(), "username", errors, "localize:user.data.column.username");
		ValidationUtils.testMaxLength(user.getUsername(), "username", errors, MAX_CHARACTERS, "localize:user.data.column.username");
    }

	@Override
	public Serializable create(UserDTO user) {
		user.setPassword(DigestUtils.sha256Hex(user.getPassword()));
		return super.create(user);
	}

	@Override
	public void update(UserDTO user) {
		UserDTO originalUser = get(user.getId());
		if (!StringUtils.isEmpty(user.getPassword())) {
			user.setPassword(DigestUtils.sha256Hex(user.getPassword()));		
		}
		else {
			user.setPassword(originalUser.getPassword());
		}
		super.update(user);
	}

	public List<UserDTO> list() {
		return AssemblerUtils.asList(userDao.list(), userAssembler::createDTO);
	}

	public UserDTO get(String username) {
		return userAssembler.createDTO(userDao.get(username));
	}

	@Override
	protected RuntimeException onCreateFailure(User user, RuntimeException cause) {
		user.setId(null);
		return onUpdateFailure(user, cause);
	}

	@Override
	protected RuntimeException onUpdateFailure(User user, RuntimeException cause) {
		if (cause instanceof DataIntegrityViolationException) {
			return new ValidationException("user.validation.message.duplicateEntry", userAssembler.createDTO(user), user.getUsername());
		}
		return super.onUpdateFailure(user, cause);
	}

	@Override
	protected RuntimeException onGetFailure(Integer id, RuntimeException cause) {
		if (cause instanceof DataRetrievalFailureException) {
			return new ValidationException("user.validation.message.notFound", new UserDTO(), id);		
		}
		return super.onGetFailure(id, cause);
	}
}