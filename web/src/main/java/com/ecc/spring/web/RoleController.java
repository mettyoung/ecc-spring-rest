package com.ecc.spring.web;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;

import com.ecc.spring.dto.RoleDTO;
import com.ecc.spring.service.RoleService;
import com.ecc.spring.util.NumberUtils;
import com.ecc.spring.util.ValidationUtils;
import com.ecc.spring.util.ValidationException;

@Controller
@RequestMapping("/roles")
public class RoleController {
	private static final String DEFAULT_COMMAND_NAME = "command";
	private static final String QUERY_PARAMETER_ROLE_ID = "id";

	private static final String VIEW_PARAMETER_ERROR_MESSAGES = "errorMessages";
	private static final String VIEW_PARAMETER_SUCCESS_MESSAGE = "successMessage";
	private static final String VIEW_PARAMETER_HEADER = "headerTitle";
	private static final String VIEW_PARAMETER_ACTION = "action";
	private static final String VIEW_PARAMETER_DATA = "data";

	private static final String ATTRIBUTE_FORCE_CREATE_MODE = "isCreateMode";

	private static final Logger logger = LoggerFactory.getLogger(RoleController.class);

	@Autowired
	private RoleService roleService;

	@Autowired
	private MessageSource messageSource;

	@InitBinder
	protected void initBinder(WebDataBinder binder) {
		binder.setValidator(roleService);
	}

	@RequestMapping(method = RequestMethod.GET)
	public ModelAndView list(HttpServletRequest request, Locale locale) {
		ModelAndView modelView = new ModelAndView("role");

		RoleDTO role = new RoleDTO();
		Integer roleId = NumberUtils.createInteger(request.getParameter(QUERY_PARAMETER_ROLE_ID));
		if (request.getAttribute(ATTRIBUTE_FORCE_CREATE_MODE) != null || roleId == null) {
			modelView.addObject(VIEW_PARAMETER_HEADER, messageSource.getMessage("role.headerTitle.create", null, locale));
			modelView.addObject(VIEW_PARAMETER_ACTION, "/create");
		}
		else {
			try {
				role = roleService.get(roleId);
			}
			catch (DataRetrievalFailureException cause) {
				request.setAttribute(ATTRIBUTE_FORCE_CREATE_MODE, true);
				throw new ValidationException("role.validation.message.notFound", new RoleDTO(), roleId);		
			}
			modelView.addObject(VIEW_PARAMETER_HEADER, messageSource.getMessage("role.headerTitle.update", null, locale));
			modelView.addObject(VIEW_PARAMETER_ACTION, "/update");
		}
		modelView.addObject(DEFAULT_COMMAND_NAME, role);

		if (RequestContextUtils.getInputFlashMap(request) != null) {
			modelView.addObject(VIEW_PARAMETER_SUCCESS_MESSAGE, RequestContextUtils.getInputFlashMap(request).get(VIEW_PARAMETER_SUCCESS_MESSAGE));
		}
		modelView.addObject(VIEW_PARAMETER_DATA, roleService.list());
		return modelView;
	}

	@RequestMapping(value = "/create", method = RequestMethod.POST)
	public String create(HttpServletRequest request, @Validated RoleDTO role, BindingResult bindingResult, Locale locale) {
		if (bindingResult.hasErrors()) {
			throw new ValidationException(bindingResult.getAllErrors(), role);
		}

		try {
			roleService.create(role);		
		}
		catch(DataIntegrityViolationException cause) {
			throw new ValidationException("role.validation.message.duplicateEntry", role, role.getName());
		}

		String message = messageSource.getMessage("role.successMessage.create", new Object[] {role.getName()}, locale);
		RequestContextUtils.getOutputFlashMap(request).put(VIEW_PARAMETER_SUCCESS_MESSAGE, message);
		return "redirect:/roles";
	}

	@RequestMapping(value = "/update", method = RequestMethod.POST)
	public String update(HttpServletRequest request, @Validated RoleDTO role, BindingResult bindingResult, Locale locale) {
		if (bindingResult.hasErrors()) {
			throw new ValidationException(bindingResult.getAllErrors(), role);
		}
	
		try {
			roleService.update(role);		
			role = roleService.get(role.getId());		
		}
		catch(DataRetrievalFailureException cause) {
			throw new ValidationException("role.validation.message.notFound", new RoleDTO(), role.getId());		
		}
		catch(DataIntegrityViolationException cause) {
			throw new ValidationException("role.validation.message.duplicateEntry", role, role.getName());
		}

		String message = messageSource.getMessage("role.successMessage.update", new Object[] { role.getName() }, locale);
		if (role.getPersons().size() > 0) {
			String personNames = role.getPersons().stream()
				.map(person -> person.getName().toString())
				.collect(Collectors.joining("; "));
			message += " " + messageSource.getMessage("role.successMessage.affectedPersons", new Object[] { personNames }, locale);
		}
		RequestContextUtils.getOutputFlashMap(request).put(VIEW_PARAMETER_SUCCESS_MESSAGE, message);
		return "redirect:/roles";
	}

	@RequestMapping(value = "/delete", method = RequestMethod.POST)
	public String delete(HttpServletRequest request, RoleDTO role, Locale locale) {
		request.setAttribute(ATTRIBUTE_FORCE_CREATE_MODE, true);
		try {
			role = roleService.get(role.getId());
			roleService.delete(role.getId());
		}
		catch(DataIntegrityViolationException cause) {
			if (role.getPersons().size() > 0) {
				String personNames = role.getPersons()
					.stream()
					.map(person -> person.getName().toString())
					.collect(Collectors.joining("; "));
				throw new ValidationException("role.validation.message.inUsed", role, personNames);
			}
			throw cause;
		}
		catch (DataRetrievalFailureException cause) {
			throw new ValidationException("role.validation.message.notFound", new RoleDTO(), role.getId());		
		}

		String message = messageSource.getMessage("role.successMessage.delete", new Object[] { role.getName() }, locale);
		RequestContextUtils.getOutputFlashMap(request).put(VIEW_PARAMETER_SUCCESS_MESSAGE, message);
		return "redirect:/roles";
	}

  @ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler({ ValidationException.class })
	public ModelAndView exceptionHandler(HttpServletRequest request, ValidationException cause, Locale locale) {
		ModelAndView modelView = list(request, locale);
		List<ObjectError> errors = cause.getAllErrors();
		Object target = cause.getTarget();
	
		if (request.getAttribute(ATTRIBUTE_FORCE_CREATE_MODE) == null) {
			modelView.addObject(DEFAULT_COMMAND_NAME, target);				
		}
		modelView.addObject(VIEW_PARAMETER_ERROR_MESSAGES, ValidationUtils.localize(errors, messageSource, locale));

		for (String message : ValidationUtils.localize(errors, messageSource, Locale.ENGLISH)) {
			logger.info(message, cause);
		}
		return modelView;
	}
}