package com.tmon.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import io.swagger.models.Model;
import springfox.documentation.annotations.ApiIgnore;

@ApiIgnore
@Controller 
public class MainController {
 	
	@RequestMapping("/") 
	public ModelAndView ScheduleManagementRoot(Model model) throws Exception{ 
		ModelAndView mav = new ModelAndView("redirect:/swagger-ui.html"); 		
		return mav; 
	} 
}
 
 