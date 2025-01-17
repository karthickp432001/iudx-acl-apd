package iudx.apd.acl.server.policy;

import static iudx.apd.acl.server.apiserver.util.Constants.*;
import static iudx.apd.acl.server.common.HttpStatusCode.*;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import iudx.apd.acl.server.apiserver.util.ResourceObj;
import iudx.apd.acl.server.apiserver.util.Util;
import iudx.apd.acl.server.common.HttpStatusCode;
import iudx.apd.acl.server.common.ResponseUrn;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import iudx.apd.acl.server.validation.exceptions.DxRuntimeException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CatalogueClient implements CatalogueClientInterface {

  private static final Logger LOGGER = LogManager.getLogger(CatalogueClient.class);
  private final String catHost;
  private final Integer catPort;
  private final String catRelationShipPath;
  private final String apdURL;
  WebClient client;

  public CatalogueClient(JsonObject options) {
    WebClientOptions clientOptions =
        new WebClientOptions().setSsl(true).setVerifyHost(false).setTrustAll(true);
    if (this.client == null) {
      this.client = WebClient.create(Vertx.vertx(), clientOptions);
    }
    this.apdURL = options.getString(APD_URL);
    this.catHost = options.getString("catServerHost");
    this.catPort = options.getInteger("catServerPort");
    this.catRelationShipPath = options.getString("dxCatalogueBasePath") + RELATIONSHIP_PATH;
  }

  @Override
  public Future<List<ResourceObj>> fetchItems(Set<UUID> ids) {
    Promise<List<ResourceObj>> promise = Promise.promise();
    List<ResourceObj> resourceObjList = new ArrayList<>();
    for (UUID id : ids) {
      client
          .get(catPort, catHost, catRelationShipPath)
          .addQueryParam(ID, String.valueOf(id))
          .addQueryParam("rel", "all")
          .send()
          .onFailure(
              ar -> {
                LOGGER.error("fetchItem error : " + ar.getMessage());
                promise.fail(INTERNAL_SERVER_ERROR.getDescription());
              })
          .onSuccess(
              catSuccessResponse -> {
                JsonObject resultBody = catSuccessResponse.bodyAsJsonObject();
                LOGGER.info(resultBody.encodePrettily());
                if (resultBody.getString(TYPE).equals(CAT_SUCCESS_URN)) {
                  List<JsonObject> resultJsonList =
                      resultBody.getJsonArray(RESULTS).stream()
                          .map(obj -> (JsonObject) obj)
                          .collect(Collectors.toList());
                  UUID provider = null;
                  UUID resourceGroup = null;
                  String resServerUrl = null;
                  boolean isItemGroupLevelResource = false;
                  boolean isProviderId = false;
                  boolean isResourceServerId = false;
                  boolean isInvalidId = false;
                  String apdUrlOfResource = "";

                  for (JsonObject resultJson : resultJsonList) {
                    String type = resultJson.getString(TYPE);
                    String idFromResponse = resultJson.getString(ID);
                    /* check if the id being sent is a provider id*/
                    if (type.contains(PROVIDER_TAG)) {
                      isProviderId = idFromResponse.equals(id.toString());
                    }
                    /* check if the id being sent is a resource server id*/
                    if (type.contains(RESOURCE_SERVER_TAG)) {
                      isResourceServerId = idFromResponse.equals(id.toString());
                    }

                    isInvalidId = isProviderId || isResourceServerId;
                    if (!isInvalidId
                        && idFromResponse != null
                        && idFromResponse.equals(id.toString())) {
                      List<String> tags = Util.toList(resultJson.getJsonArray(TYPE));
                      isItemGroupLevelResource = tags.contains(RESOURCE_GROUP_TAG);
                    }

                    JsonArray typeArray = resultJson.getJsonArray(TYPE);
                    if (typeArray.contains(RESOURCE_GROUP_TAG)) {
                      resourceGroup = UUID.fromString(idFromResponse);
                    } else if (typeArray.contains(PROVIDER_TAG)) {
                      provider = UUID.fromString(resultJson.getString(OWNER_ID));
                    } else if (typeArray.contains(RESOURCE_TAG)) {
                      resServerUrl = resultJson.getString(RS_URL);
                    } else if (typeArray.contains(RESOURCE_ITEM_TAG)) {
                      apdUrlOfResource = resultJson.getString(APD_URL);
                    }
                  }
                  boolean isInfoFromCatInvalid =
                      id == null || provider == null || resServerUrl == null;
                  if (isInfoFromCatInvalid && !isInvalidId) {
                    LOGGER.error("Something from catalogue is null. The resourceId is {}", id);
                    LOGGER.error("The ownerId is {}", provider);
                    LOGGER.error("The resource server URL is {}", resServerUrl);
                    String failureMessage =
                        generateFailureMessage(
                            INTERNAL_SERVER_ERROR,
                            ResponseUrn.INTERNAL_SERVER_ERROR,
                            "Something went wrong while fetching resource info from Catalogue");

                    promise.fail(failureMessage);
                  } else if (isProviderId || isResourceServerId) {
                    LOGGER.error("isProviderId: {}", isProviderId);
                    LOGGER.error("isResourceServerId: {}", isResourceServerId);
                    String failureMessage =
                        generateFailureMessage(
                            BAD_REQUEST,
                            ResponseUrn.BAD_REQUEST_URN,
                            "Given id is invalid - it is a provider or resource server id");

                    promise.fail(failureMessage);
                  } else if (isItemGroupLevelResource) {

                    String failureMessage =
                        generateFailureMessage(
                            BAD_REQUEST,
                            ResponseUrn.BAD_REQUEST_URN,
                            "Given id is invalid - it is group level resource");
                    promise.fail(failureMessage);
                  }
                  /* if the resource has an APD URL that is not equal to the current APD URL*/
                  else if (!isItemGroupLevelResource && !apdURL.equals(apdUrlOfResource)) {
                    String failureMessage =
                        generateFailureMessage(
                            FORBIDDEN,
                            ResponseUrn.FORBIDDEN_URN,
                            "Resource is forbidden to access, as the APD URL for the resource : "
                                + apdUrlOfResource
                                + " is different than the current APD : "
                                + apdURL);
                    promise.fail(failureMessage);
                  } else {
                    ResourceObj resourceObj =
                        new ResourceObj(
                            id, provider, resourceGroup, resServerUrl, isItemGroupLevelResource);
                    resourceObjList.add(resourceObj);
                    promise.complete(resourceObjList);
                  }
                } else {
                  promise.fail(resultBody.getString(DETAIL));
                }
              });
    }
    return promise.future();
  }

  public String generateFailureMessage(
      HttpStatusCode httpStatusCode, ResponseUrn responseUrn, String detail) {
    return new JsonObject()
        .put(TYPE, httpStatusCode.getValue())
        .put(TITLE, responseUrn.getUrn())
        .put(DETAIL, detail)
        .encode();
  }
}
