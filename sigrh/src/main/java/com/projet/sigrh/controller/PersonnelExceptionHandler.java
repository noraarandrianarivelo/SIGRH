package com.projet.sigrh.controller;

import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.validation.BindException;
import com.projet.sigrh.exception.BusinessException;
import jakarta.servlet.http.HttpServletResponse;

@ControllerAdvice(assignableTypes=PersonnelController.class)
@Order(0)
public class PersonnelExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public String metier(BusinessException e,HttpServletResponse response,Model m) {
        return erreur(e.getStatus(),e.getMessage(),response,m);
    }
    @ExceptionHandler({DataIntegrityViolationException.class,ConcurrencyFailureException.class})
    public String conflit(Exception e,HttpServletResponse response,Model m) {
        return erreur(HttpStatus.CONFLICT,"Les données ont changé ou cette ressource est déjà utilisée. Rechargez la page et vérifiez votre saisie.",response,m);
    }
    @ExceptionHandler({BindException.class,ServletRequestBindingException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public String saisie(Exception e,HttpServletResponse response,Model m) {
        return erreur(HttpStatus.BAD_REQUEST,"Certains champs sont manquants ou invalides. Vérifiez les dates, les montants et les choix du formulaire.",response,m);
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String taille(Exception e,HttpServletResponse response,Model m) {
        return erreur(HttpStatus.BAD_REQUEST,"Le PDF dépasse la taille autorisée de 10 Mio.",response,m);
    }
    private String erreur(HttpStatus statut,String message,HttpServletResponse response,Model m) {
        response.setStatus(statut.value()); m.addAttribute("message",message); m.addAttribute("code",statut.value()); return "personnel/erreur";
    }
}
