package pe.gob.pki.firma_digital;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class StatusController {

    @GetMapping("/status")
    public String status(){
        return "OK - Sistema activo";
    }

}
