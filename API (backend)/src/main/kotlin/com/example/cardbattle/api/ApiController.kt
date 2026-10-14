import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ApiController {
    @GetMapping("/api")
    fun getApi(): String {
        return "hello from the api"
    }


}