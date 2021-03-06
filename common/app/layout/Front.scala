package layout

import com.gu.facia.client.models.CollectionConfig
import dfp.DfpAgent
import model._
import org.joda.time.DateTime
import services.CollectionConfigWithId
import slices._
import views.support.CutOut
import scala.Function._
import slices.MostPopular

/** For de-duplicating cutouts */
object ContainerLayoutContext {
  val empty = ContainerLayoutContext(Set.empty, false)
}

case class ContainerLayoutContext(
  cutOutsSeen: Set[CutOut],
  hideCutOuts: Boolean
) {
  def addCutOuts(cutOut: Set[CutOut]) = copy(cutOutsSeen = cutOutsSeen ++ cutOut)

  def transform(card: FaciaCardAndIndex) = {
    if (hideCutOuts) {
      (card.transformCard(_.copy(cutOut = None)), this)
    } else {
      // Latest snaps are sometimes used to promote journalists, and the cut out should always show on these snaps.
      card.item match {
        case content: ContentCard =>
          if (content.snapStuff.snapType == LatestSnap) {
            (card, this)
          } else {
            val newCard = if (content.cutOut.exists(cutOutsSeen.contains)) {
              card.copy(item = content.copy(cutOut = None))
            } else {
              card
            }
            (newCard, addCutOuts(content.cutOut.filter(const(content.cardTypes.showCutOut)).toSet))
          }

        case _ => (card, this)
      }
    }
  }
}

object CollectionEssentials {
  def fromCollection(collection: Collection) = CollectionEssentials(
    collection.items,
    collection.displayName,
    collection.href,
    collection.lastUpdated,
    if (collection.curated.isEmpty) Some(9) else None
  )

  def fromTrails(trails: Seq[Trail]) = CollectionEssentials(
    trails,
    None,
    None,
    None,
    None
  )

  val empty = CollectionEssentials(Nil, None, None, None, None)
}

case class CollectionEssentials(
  items: Seq[Trail],
  displayName: Option[String],
  href: Option[String],
  lastUpdated: Option[String],
  showMoreLimit: Option[Int]
)

object ContainerCommercialOptions {
  def fromConfig(config: CollectionConfig) = ContainerCommercialOptions(
    DfpAgent.isSponsored(config),
    DfpAgent.isAdvertisementFeature(config),
    DfpAgent.isFoundationSupported(config),
    DfpAgent.sponsorshipTag(config),
    DfpAgent.sponsorshipType(config)
  )

  def fromMetaData(metaData: MetaData) = ContainerCommercialOptions(
    metaData.isSponsored,
    metaData.isAdvertisementFeature,
    metaData.isFoundationSupported,
    metaData.sponsor,
    metaData.sponsorshipType
  )

  val empty = ContainerCommercialOptions(
    false,
    false,
    false,
    None,
    None
  )
}

case class ContainerCommercialOptions(
  isSponsored: Boolean,
  isAdvertisementFeature: Boolean,
  isFoundationSupported: Boolean,
  sponsorshipTag: Option[String],
  sponsorshipType: Option[String]
) {
  val isPaidFor = isSponsored || isAdvertisementFeature || isFoundationSupported
}

object FaciaContainer {
  def apply(
    index: Int,
    container: Container,
    config: CollectionConfigWithId,
    collectionEssentials: CollectionEssentials,
    componentId: Option[String] = None
  ): FaciaContainer = {
    apply(
      index,
      container,
      ContainerDisplayConfig.withDefaults(config),
      collectionEssentials,
      componentId
    )
  }

  def apply(
    index: Int,
    container: Container,
    config: ContainerDisplayConfig,
    collectionEssentials: CollectionEssentials,
    componentId: Option[String]
  ): FaciaContainer = fromConfig(
    index,
    container,
    config.collectionConfigWithId,
    collectionEssentials,
    ContainerLayout.fromContainer(
      container,
      ContainerLayoutContext.empty,
      config,
      collectionEssentials.items
    ).map(_._1),
    componentId
  )

  def fromConfig(
    index: Int,
    container: Container,
    config: CollectionConfigWithId,
    collectionEssentials: CollectionEssentials,
    containerLayout: Option[ContainerLayout],
    componentId: Option[String]
  ): FaciaContainer = FaciaContainer(
    index,
    config.id,
    config.config.displayName orElse collectionEssentials.displayName,
    config.config.href orElse collectionEssentials.href,
    componentId,
    container,
    collectionEssentials,
    containerLayout,
    config.config.showDateHeader.exists(identity),
    config.config.showLatestUpdate.exists(identity),
    // popular containers should never be sponsored
    container match {
      case MostPopular => ContainerCommercialOptions.empty
      case _ => ContainerCommercialOptions.fromConfig(config.config)
    },
    None,
    None,
    false,
    false,
    None
  )

  def forStoryPackage(dataId: String, items: Seq[Trail], title: String) = {
    FaciaContainer(
      index = 2,
      container = Fixed(ContainerDefinition.fastForNumberOfItems(items.size)),
      config = ContainerDisplayConfig.withDefaults(CollectionConfigWithId(dataId, CollectionConfig.emptyConfig)),
      collectionEssentials = CollectionEssentials(items take 8, Some(title), None, None, None),
      componentId = None
    ).withTimeStamps
  }
}

case class FaciaContainer(
  index: Int,
  dataId: String,
  displayName: Option[String],
  href: Option[String],
  componentId: Option[String],
  container: Container,
  collectionEssentials: CollectionEssentials,
  containerLayout: Option[ContainerLayout],
  showDateHeader: Boolean,
  showLatestUpdate: Boolean,
  commercialOptions: ContainerCommercialOptions,
  customHeader: Option[FaciaContainerHeader],
  customClasses: Option[Seq[String]],
  hideToggle: Boolean,
  showTimestamps: Boolean,
  dateLinkPath: Option[String]
) {
  def transformCards(f: ContentCard => ContentCard) = copy(
    containerLayout = containerLayout.map(_.transformCards(f))
  )

  def faciaComponentName = componentId getOrElse {
    displayName map { title: String =>
      title.toLowerCase.replace(" ", "-")
    } getOrElse "no-name"
  }

  def latestUpdate = (collectionEssentials.items.map(_.webPublicationDate) ++
    collectionEssentials.lastUpdated.map(DateTime.parse(_))).sortBy(-_.getMillis).headOption

  def items = collectionEssentials.items

  def contentItems = items collect { case c: Content => c }

  def withTimeStamps = transformCards(_.withTimeStamp)

  def dateLink: Option[String] = {
    val maybeDateHeadline = customHeader map {
      case MetaDataHeader(_, _, _, dateHeadline, _) => dateHeadline
      case LoneDateHeadline(dateHeadline) => dateHeadline
    }

    for {
      path <- dateLinkPath
      dateHeadline <- maybeDateHeadline
      urlFragment <- dateHeadline.urlFragment
    } yield s"$path/$urlFragment/all"
  }
}

object Front extends implicits.Collections {
  type TrailUrl = String

  def itemsVisible(containerDefinition: ContainerDefinition) =
    containerDefinition.slices.flatMap(_.layout.columns.map(_.numItems)).sum

  // Never de-duplicate snaps.
  def participatesInDeduplication(trail: Trail) = !trail.snapType.isDefined

  /** Given a set of already seen trail URLs, a container type, and a set of trails, returns a new set of seen urls
    * for further de-duplication and the sequence of trails in the order that they ought to be shown for that
    * container.
    */
  def deduplicate(
    seen: Set[TrailUrl],
    container: Container,
    trails: Seq[Trail]
  ): (Set[TrailUrl], Seq[Trail]) = {
    container match {
      case Dynamic(dynamicContainer) =>
        /** Although Dynamic Containers participate in de-duplication, insofar as trails that appear in Dynamic
          * Containers will not be duplicated further down on the page, they themselves retain all their trails, no
          * matter what occurred further up the page.
          */
        dynamicContainer.containerDefinitionFor(
          trails.collect({ case content: Content => content }).map(Story.fromContent)
        ) map { containerDefinition =>
          (seen ++ trails
            .map(_.url)
            .take(itemsVisible(containerDefinition)), trails)
        } getOrElse {
          (seen, trails)
        }

      /** Singleton containers (such as the eyewitness one or the thrasher one) do not participate in deduplication */
      case Fixed(containerDefinition) if containerDefinition.isSingleton =>
        (seen, trails)

      case Fixed(containerDefinition) =>
        /** Fixed Containers participate in de-duplication.
          */
        val nToTake = itemsVisible(containerDefinition)
        val notUsed = trails.filter(trail => !seen.contains(trail.url) || !participatesInDeduplication(trail))
          .distinctBy(_.url)
        (seen ++ notUsed.take(nToTake).filter(participatesInDeduplication).map(_.url), notUsed)

      case _ =>
        /** Nav lists and most popular do not participate in de-duplication at all */
        (seen, trails)
    }
  }

  def fromConfigsAndContainers(
    configs: Seq[((ContainerDisplayConfig, CollectionEssentials), Container)],
    initialContext: ContainerLayoutContext = ContainerLayoutContext.empty
  ) = {
    import scalaz.syntax.traverse._
    import scalaz.std.list._

    Front(
      configs.zipWithIndex.toList.mapAccumL(
        (Set.empty[TrailUrl], initialContext)
      ) { case ((seenTrails, context), (((config, collection), container), index)) =>
        val (newSeen, newItems) = deduplicate(seenTrails, container, collection.items)

        ContainerLayout.fromContainer(container, context, config, newItems) map {
          case (containerLayout, newContext) => ((newSeen, newContext), FaciaContainer.fromConfig(
            index,
            container,
            config.collectionConfigWithId,
            collection.copy(items = newItems),
            Some(containerLayout),
            None
          ))
        } getOrElse {
          ((newSeen, context), FaciaContainer.fromConfig(
            index,
            container,
            config.collectionConfigWithId,
            collection.copy(items = newItems),
            None,
            None
          ))
        }
      }._2.filterNot(_.items.isEmpty)
    )
  }

  def fromConfigs(configs: Seq[(CollectionConfigWithId, CollectionEssentials)]) = {
    fromConfigsAndContainers(configs.map {
      case (config, collectionEssentials) =>
        ((ContainerDisplayConfig.withDefaults(config), collectionEssentials), Container.fromConfig(config.config))
    })
  }
}

case class Front(
  containers: Seq[FaciaContainer]
)
