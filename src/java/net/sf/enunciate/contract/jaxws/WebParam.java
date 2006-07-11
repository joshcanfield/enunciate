package net.sf.enunciate.contract.jaxws;

import com.sun.mirror.declaration.ClassDeclaration;
import com.sun.mirror.declaration.ParameterDeclaration;
import com.sun.mirror.declaration.TypeDeclaration;
import com.sun.mirror.type.DeclaredType;
import com.sun.mirror.type.TypeMirror;
import net.sf.enunciate.apt.EnunciateFreemarkerModel;
import net.sf.enunciate.contract.jaxb.RootElementDeclaration;
import net.sf.enunciate.contract.jaxb.types.XmlTypeException;
import net.sf.enunciate.contract.jaxb.types.XmlTypeMirror;
import net.sf.enunciate.contract.validation.ValidationException;
import net.sf.enunciate.util.QName;
import net.sf.jelly.apt.decorations.declaration.DecoratedParameterDeclaration;
import net.sf.jelly.apt.decorations.type.DecoratedTypeMirror;
import net.sf.jelly.apt.freemarker.FreemarkerModel;

import javax.jws.soap.SOAPBinding;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

/**
 * @author Ryan Heaton
 */
public class WebParam extends DecoratedParameterDeclaration implements WebMessage, WebMessagePart, ImplicitRootElement, ImplicitChildElement {

  private final javax.jws.WebParam annotation;
  private final WebMethod method;

  protected WebParam(ParameterDeclaration delegate, WebMethod method) {
    super(delegate);

    this.method = method;
    if (this.method == null) {
      throw new IllegalArgumentException("A web method must be provided.");
    }

    annotation = delegate.getAnnotation(javax.jws.WebParam.class);
  }

  /**
   * The web method for this web param.
   *
   * @return The web method for this web param.
   */
  public WebMethod getWebMethod() {
    return method;
  }

  /**
   * The name of this web param.
   *
   * @return The name of this web param.
   */
  public String getElementName() {
    String name = getSimpleName();

    if ((annotation != null) && (annotation.name() != null) && (!"".equals(annotation.name()))) {
      name = annotation.name();
    }

    return name;
  }

  /**
   * The doc comment associated with this web param.
   *
   * @return The doc comment associated with this web param.
   */
  public String getElementDocs() {
    return getDelegate().getDocComment();
  }

  /**
   * The part name of the message for this parameter.
   *
   * @return The part name of the message for this parameter.
   */
  public String getPartName() {
    return getSimpleName();
  }

  /**
   * The message name of the message for this parameter.
   *
   * @return The message name of the message for this parameter.
   */
  public String getMessageName() {
    return method.getMessageName();
  }

  /**
   * There is only message documentation if this web parameter is BARE.
   *
   * @return The documentation if BARE, null otherwise.
   */
  public String getMessageDocs() {
    if (isBare()) {
      return getDelegate().getDocComment();
    }

    return null;
  }

  /**
   * There is only part documentation if this web parameter is not BARE.
   *
   * @return null if BARE, the documantation otherwise.
   */
  public String getPartDocs() {
    if (isBare()) {
      return null;
    }

    return getDelegate().getDocComment();
  }

  /**
   * The qname of the element for this part.
   *
   * @return The qname of the element for this part.
   */
  public QName getElementQName() {
    TypeMirror parameterType = getType();
    if (parameterType instanceof DeclaredType) {
      TypeDeclaration parameterTypeDeclaration = ((DeclaredType) parameterType).getDeclaration();
      if ((parameterTypeDeclaration instanceof ClassDeclaration) && (parameterTypeDeclaration.getAnnotation(XmlRootElement.class) != null)) {
        EnunciateFreemarkerModel model = ((EnunciateFreemarkerModel) FreemarkerModel.get());
        RootElementDeclaration rootElement = model.findRootElementDeclaration((ClassDeclaration) parameterTypeDeclaration);
        if (rootElement == null) {
          throw new ValidationException(getPosition(), parameterTypeDeclaration.getQualifiedName() +
            " is not a known root element.  Please add it to the list of known classes.");
        }

        return new QName(rootElement.getQualifiedName(), rootElement.getName());
      }
    }

    return new QName(method.getDeclaringEndpointInterface().getTargetNamespace(), getElementName());
  }

  /**
   * This web parameter defines an implicit schema element if it is NOT of a class type that is an xml root element.
   *
   * @return Whether this web parameter is an implicit schema element.
   */
  public boolean isImplicitSchemaElement() {
    TypeMirror parameterType = getType();
    return !((parameterType instanceof DeclaredType) && (((DeclaredType) parameterType).getDeclaration().getAnnotation(XmlRootElement.class) != null));
  }

  /**
   * A web param never has any child elements.  It is either a BARE root element with a non-anonymous type, or a child
   * element of a request/response wrapper.
   *
   * @return null.
   */
  public Collection<ImplicitChildElement> getChildElements() {
    return null;
  }

  /**
   * The qname of the type of this parameter.
   *
   * @return The qname of the type of this parameter.
   * @throws ValidationException If the type is anonymous or otherwise problematic.
   */
  public QName getTypeQName() {
    try {
      EnunciateFreemarkerModel model = ((EnunciateFreemarkerModel) FreemarkerModel.get());
      XmlTypeMirror xmlType = model.getXmlType(getType());
      if (xmlType.isAnonymous()) {
        throw new ValidationException(getPosition(), "Type of web parameter cannot be anonymous.");
      }

      return xmlType.getQname();
    }
    catch (XmlTypeException e) {
      throw new ValidationException(getPosition(), e.getMessage());
    }
  }

  public int getMinOccurs() {
    DecoratedTypeMirror paramType = (DecoratedTypeMirror) getType();
    return paramType.isPrimitive() ? 1 : 0;
  }

  public String getMaxOccurs() {
    DecoratedTypeMirror paramType = (DecoratedTypeMirror) getType();
    return paramType.isArray() || paramType.isCollection() ? "unbounded" : "1";
  }

  /**
   * The mode of this web param.
   *
   * @return The mode of this web param.
   */
  public javax.jws.WebParam.Mode getMode() {
    javax.jws.WebParam.Mode mode = javax.jws.WebParam.Mode.IN;

    if ((annotation != null) && (annotation.mode() != null)) {
      mode = annotation.mode();
    }

    return mode;
  }

  /**
   * Whether this is a header param.
   *
   * @return Whether this is a header param.
   */
  public boolean isHeader() {
    boolean header = false;

    if (annotation != null) {
      header = annotation.header();
    }

    return header;
  }

  /**
   * Whether this is a bare web param.
   *
   * @return Whether this is a bare web param.
   */
  private boolean isBare() {
    return method.getSoapParameterStyle() == SOAPBinding.ParameterStyle.BARE;
  }

  /**
   * Whether this is an input message depends on its mode.
   *
   * @return Whether this is an input message depends on its mode.
   */
  public boolean isInput() {
    return (getMode() == javax.jws.WebParam.Mode.IN) || (getMode() == javax.jws.WebParam.Mode.INOUT);
  }

  /**
   * Whether this is an output message depends on its mode.
   *
   * @return Whether this is an output message depends on its mode.
   */
  public boolean isOutput() {
    return (getMode() == javax.jws.WebParam.Mode.OUT) || (getMode() == javax.jws.WebParam.Mode.INOUT);
  }

  /**
   * @return false
   */
  public boolean isFault() {
    return false;
  }

  /**
   * If this web param is complex, it will only have one part: itself.
   *
   * @return this.
   * @throws UnsupportedOperationException if this web param isn't complex.
   */
  public Collection<WebMessagePart> getParts() {
    if (!isBare()) {
      throw new UnsupportedOperationException("Web param doesn't represent a complex method input/output.");
    }

    return new ArrayList<WebMessagePart>(Arrays.asList(this));
  }

}
