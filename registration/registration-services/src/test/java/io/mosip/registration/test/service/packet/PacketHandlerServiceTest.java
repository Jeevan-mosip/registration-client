package io.mosip.registration.test.service.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import io.mosip.registration.dto.schema.ProcessSpecDto;
import io.mosip.registration.enums.FlowType;
import io.mosip.registration.enums.Role;
import io.mosip.registration.service.config.LocalConfigService;
import io.mosip.registration.service.sync.PolicySyncService;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import io.mosip.kernel.core.idgenerator.spi.PridGenerator;
import io.mosip.kernel.core.idgenerator.spi.RidGenerator;
import io.mosip.kernel.core.util.FileUtils;
import io.mosip.kernel.core.util.JsonUtils;
import io.mosip.registration.audit.AuditManagerSerivceImpl;
import io.mosip.registration.constants.RegistrationClientStatusCode;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.context.ApplicationContext;
import io.mosip.registration.context.SessionContext;
import io.mosip.registration.dao.RegistrationCenterDAO;
import io.mosip.registration.dao.RegistrationDAO;
import io.mosip.registration.dao.impl.RegistrationDAOImpl;
import io.mosip.registration.dto.PacketStatusDTO;
import io.mosip.registration.dto.RegistrationDTO;
import io.mosip.registration.dto.ResponseDTO;
import io.mosip.registration.dto.SuccessResponseDTO;
import io.mosip.registration.dto.schema.UiFieldDTO;
import io.mosip.registration.entity.MachineMaster;
import io.mosip.registration.entity.Registration;
import io.mosip.registration.exception.RegBaseCheckedException;
import io.mosip.registration.exception.RegistrationExceptionConstants;
import io.mosip.registration.repositories.MachineMasterRepository;
import io.mosip.registration.service.BaseService;
import io.mosip.registration.service.IdentitySchemaService;
import io.mosip.registration.service.config.GlobalParamService;
import io.mosip.registration.service.operator.UserDetailService;
import io.mosip.registration.service.packet.impl.PacketHandlerServiceImpl;
import io.mosip.registration.service.remap.CenterMachineReMapService;
import io.mosip.registration.util.healthcheck.RegistrationSystemPropertiesChecker;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({ "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "javax.management.*" })
@PrepareForTest({ JsonUtils.class, ApplicationContext.class, SessionContext.class, Role.class, FileUtils.class })
public class PacketHandlerServiceTest {
	@Rule
	public MockitoRule mockitoRule = MockitoJUnit.rule();
	
	@Mock
	private RegistrationDAOImpl registrationDAOImpl;
	
	@InjectMocks
	private PacketHandlerServiceImpl packetHandlerServiceImpl;
	
	@Mock
	private BaseService baseService;
	
	@Mock
	private RegistrationCenterDAO registrationCenterDAO;
	
	@Mock
	private GlobalParamService globalParamService;
	
	@Mock
	private AuditManagerSerivceImpl auditFactory;
	private ResponseDTO mockedSuccessResponse;

	@Mock
	private IdentitySchemaService identitySchemaService;

	@Mock
	private RidGenerator<String> ridGeneratorImpl;

	@Mock
	private PridGenerator<String> pridGenerator;

	@Mock
	private UserDetailService userDetailService;

	@Mock
	private CenterMachineReMapService centerMachineReMapService;

	@Mock
	private MachineMasterRepository machineMasterRepository;

	@Mock
	private LocalConfigService localConfigService;

	@Mock
	private PolicySyncService policySyncService;

	@Before
	public void initialize() throws Exception {
		mockedSuccessResponse = new ResponseDTO();
		mockedSuccessResponse.setSuccessResponseDTO(new SuccessResponseDTO());
		PowerMockito.mockStatic(SessionContext.class);
		PowerMockito.mockStatic(ApplicationContext.class);
		PowerMockito.mockStatic(Role.class);

		SessionContext.UserContext userContext = Mockito.mock(SessionContext.UserContext.class);
		userContext.setRoles(Arrays.asList("SUPERADMIN", "SUPERVISOR"));
		PowerMockito.when(SessionContext.userContext()).thenReturn(userContext);
		
		Map<String, Object> applicationMap = new HashMap<>();
		applicationMap.put(RegistrationConstants.INITIAL_SETUP, "N");
		
		/*
		 * final FileInputStream fileInputStreamMock =
		 * PowerMockito.mock(FileInputStream.class);
		 * PowerMockito.whenNew(FileInputStream.class).withArguments(Matchers.anyString(
		 * )) .thenReturn(fileInputStreamMock);
		 */
	

		io.mosip.registration.context.ApplicationContext.setApplicationMap(applicationMap);
		
	}


	@Test
	public void startRegistration() throws RegBaseCheckedException {

		List<UiFieldDTO> defaultFields = new LinkedList<>();

		UiFieldDTO uiFieldDTO = new UiFieldDTO();
		uiFieldDTO.setGroup(RegistrationConstants.UI_SCHEMA_GROUP_FULL_NAME);
		defaultFields.add(uiFieldDTO);

		ProcessSpecDto processSpecDto = new ProcessSpecDto();
		processSpecDto.setId("test");
		processSpecDto.setFlow("NEW");

		Mockito.when(ridGeneratorImpl.generateId(Mockito.anyString(), Mockito.anyString())).thenReturn("12345678901");
		Mockito.when(identitySchemaService.getLatestEffectiveSchemaVersion()).thenReturn(2.0);
		Mockito.when(pridGenerator.generateId()).thenReturn("0987654321");
		PowerMockito.when(SessionContext.isSessionContextAvailable()).thenReturn(true);
		PowerMockito.when(ApplicationContext.applicationLanguage()).thenReturn("eng");
		
		Mockito.when(identitySchemaService.getAllFieldSpec(Mockito.anyString(),Mockito.anyDouble())).thenReturn(defaultFields);
		Mockito.when(identitySchemaService.getProcessSpecDto(Mockito.anyString(),Mockito.anyDouble())).thenReturn(processSpecDto);

		PowerMockito.when(SessionContext.userId()).thenReturn("12345");
		Mockito.when(userDetailService.isValidUser("12345")).thenReturn(true);

//		PowerMockito.when(SessionContext.userId()).thenReturn("12345");
		Mockito.when(centerMachineReMapService.isMachineRemapped()).thenReturn(false);

		String machineName = RegistrationSystemPropertiesChecker.getMachineId();
		MachineMaster machineMaster = new MachineMaster();
		machineMaster.setIsActive(true);

		machineMaster.setId("123");

		Mockito.when(machineMasterRepository.findByNameIgnoreCase(machineName.toLowerCase())).thenReturn(machineMaster);

		when(baseService.getGlobalConfigValueOf(RegistrationConstants.INITIAL_SETUP)).thenReturn(RegistrationConstants.DISABLE);

		
		when(registrationCenterDAO.isMachineCenterActive()).thenReturn(true);

		ResponseDTO responseDTO = new ResponseDTO();
		SuccessResponseDTO successResponseDTO = new SuccessResponseDTO();
		successResponseDTO.setMessage(RegistrationConstants.VALID_KEY);
		responseDTO.setSuccessResponseDTO(successResponseDTO);
		when(policySyncService.checkKeyValidation()).thenReturn(responseDTO);
		
		packetHandlerServiceImpl.startRegistration(null, FlowType.NEW.getCategory());
	}

	@Test
	public void testCreationException() throws RegBaseCheckedException {

		ResponseDTO actualResponse = packetHandlerServiceImpl.handle(new RegistrationDTO());

		Assert.assertEquals(RegistrationExceptionConstants.REG_PACKET_CREATION_ERROR_CODE.getErrorCode(),
				actualResponse.getErrorResponseDTOs().get(0).getCode());
	}

	@Test
	public void testHandlerChkException() throws RegBaseCheckedException {
		RegBaseCheckedException exception = new RegBaseCheckedException("errorCode", "errorMsg");
		Assert.assertNotNull(packetHandlerServiceImpl.handle(null).getErrorResponseDTOs());
	}

	@Test
	public void testHandlerAuthenticationException() throws RegBaseCheckedException {
		RegBaseCheckedException exception = new RegBaseCheckedException(
				RegistrationExceptionConstants.AUTH_ADVICE_USR_ERROR.getErrorCode(),
				RegistrationExceptionConstants.AUTH_ADVICE_USR_ERROR.getErrorMessage());

		Assert.assertNotNull(packetHandlerServiceImpl.handle(null).getErrorResponseDTOs());
	}
	
	@Test
	public void getAllPacketsPositive() {
		
		List<Registration> listOfRegs = new ArrayList<>();
		Registration reg1 = new Registration();
		reg1.setClientStatusCode(RegistrationClientStatusCode.CORRECTION.getCode());
		reg1.setCrDtime(Timestamp.valueOf(LocalDateTime.now()));
		reg1.setAckFilename("123456789_Ack.html");
		Registration reg2 = new Registration();
		reg2.setClientStatusCode(RegistrationClientStatusCode.CREATED.getCode());
		reg2.setCrDtime(Timestamp.valueOf(LocalDateTime.now()));
		reg2.setAckFilename("123456789_Ack.html");
		Registration reg3 = new Registration();
		reg3.setClientStatusCode(RegistrationClientStatusCode.CORRECTION.getCode());
		reg3.setCrDtime(Timestamp.valueOf(LocalDateTime.now()));
		reg3.setAckFilename("123456789_Ack.html");
		
		listOfRegs.add(reg1);
		listOfRegs.add(reg2);
		listOfRegs.add(reg3);
		
		PacketStatusDTO expectedPack1 = new PacketStatusDTO();
		expectedPack1.setPacketId("10101");
		expectedPack1.setPacketClientStatus("CORRECTION");
		expectedPack1.setPacketServerStatus("InProgress");
		
		String path = "src/test/resources/123456789.zip";
		File file = new File(path);
		
		Mockito.when(registrationDAOImpl.getAllRegistrations()).thenReturn(listOfRegs);
		Mockito.doReturn(expectedPack1).when(baseService).preparePacketStatusDto(Mockito.any(Registration.class));
		PowerMockito.mockStatic(FileUtils.class);
		PowerMockito.when(FileUtils.getFile(Mockito.anyString())).thenReturn(file);
		
		List<PacketStatusDTO> actualRegs = packetHandlerServiceImpl.getAllPackets();
		
		assertEquals(actualRegs.get(0).getPacketClientStatus(), expectedPack1.getPacketClientStatus());
	}

}
