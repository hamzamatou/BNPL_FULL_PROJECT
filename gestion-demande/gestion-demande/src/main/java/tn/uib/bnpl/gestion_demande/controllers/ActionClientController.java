package tn.uib.bnpl.gestion_demande.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.uib.bnpl.gestion_demande.services.ActionClientService;

@RestController
@RequestMapping("/api/actions-client")
public class ActionClientController {

    private final ActionClientService actionClientService;

    public ActionClientController(ActionClientService actionClientService) {
        this.actionClientService = actionClientService;
    }

    @PostMapping("/send-otp")
    public ResponseEntity<Void> sendOtp(
            @RequestParam String token,
            @RequestParam String nom,
            @RequestParam String prenom,
            @RequestParam String cin
    ) {
        actionClientService.sendOtp(token, nom, prenom, cin);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<Void> verifyOtp(
            @RequestParam String token,
            @RequestParam String otp
    ) {
        actionClientService.verifyOtp(token, otp);
        return ResponseEntity.ok().build();
    }
}
